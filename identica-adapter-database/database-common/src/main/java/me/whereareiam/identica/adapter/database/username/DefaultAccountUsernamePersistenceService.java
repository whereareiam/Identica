package me.whereareiam.identica.adapter.database.username;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.entity.username.UsernameEntity;
import me.whereareiam.identica.adapter.database.entity.username.UsernameHistoryEntity;
import me.whereareiam.identica.adapter.database.mapper.username.UsernameHistoryMapper;
import me.whereareiam.identica.adapter.database.mapper.username.UsernameMapper;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.identity.username.AccountUsername;
import me.whereareiam.identica.model.identity.username.AccountUsernameHistoryEntry;
import me.whereareiam.identica.persistence.username.AccountUsernamePersistenceService;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.UUID;

/**
 * Keeps the usernames of accounts in {@code identica_account_usernames} and appends renames to
 * {@code identica_account_username_history}.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultAccountUsernamePersistenceService implements AccountUsernamePersistenceService {
	private final Provider<Jdbi> jdbi;
	private final Provider<EntityAccess<UsernameEntity>> usernames;
	private final Provider<EntityAccess<UsernameHistoryEntity>> history;

	@Override
	public @NotNull Optional<AccountUsername> find(@NotNull UUID uniqueId) {
		try {
			return usernames.get().find(uniqueId).map(UsernameMapper::toModel);
		} catch (Exception e) {
			Logger.warn(e, "Failed to load the username of %s", uniqueId);
			return Optional.empty();
		}
	}

	@Override
	public void setSource(@NotNull UUID uniqueId, @NotNull AccountUsernameSource source) {
		try {
			usernames.get().update(uniqueId)
					.set("source", source.getId())
					.set("changed_at", System.currentTimeMillis())
					.execute();
		} catch (Exception e) {
			Logger.warn(e, "Failed to set the username source of %s", uniqueId);
		}
	}

	/**
	 * Renames the account and appends the history entry in one transaction. A new username whose key another account
	 * holds fails the update on the unique key, which leaves both unchanged.
	 */
	@Override
	public boolean rename(@NotNull AccountUsernameHistoryEntry change) {
		if (change.getNewUsername().isBlank()) return false;

		try {
			return jdbi.get().inTransaction(handle -> {
				boolean renamed = usernames.get().update(change.getUniqueId())
						.set("username", change.getNewUsername())
						.set(UsernameEntity.USERNAME_KEY, UsernameMapper.usernameKey(change.getNewUsername()))
						.set("source", change.getSource().getId())
						.set("provider_id", change.getProviderId())
						.set("changed_at", change.getChangedAt())
						.execute();
				if (renamed) history.get().insert(UsernameHistoryMapper.toEntity(change));

				return renamed;
			});
		} catch (Exception e) {
			Logger.warn(e, "Failed to rename %s to %s", change.getUniqueId(), change.getNewUsername());
			return false;
		}
	}
}
