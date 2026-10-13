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
import me.whereareiam.identica.adapter.database.repository.username.UsernameRepository;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.identity.username.AccountUsername;
import me.whereareiam.identica.model.identity.username.AccountUsernameHistoryEntry;
import me.whereareiam.identica.model.identity.username.UsernameWriteResult;
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
	private final Provider<UsernameRepository> usernameRepository;

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
	public @NotNull Optional<AccountUsername> findHolder(@NotNull String username) {
		return usernameRepository.get().findByUsernameKey(UsernameMapper.usernameKey(username)).map(UsernameMapper::toModel);
	}

	/**
	 * Renames an account in one transaction with its history entry, unless another row holds the new username's
	 * key. The key's unique index still refuses a username another account takes between the check and the
	 * write; the database's error is thrown then, because Dialectica 4.0.0 reports such a conflict of an update as
	 * an ordinary statement failure.
	 */
	@Override
	public @NotNull UsernameWriteResult rename(@NotNull AccountUsernameHistoryEntry change) {
		if (change.getNewUsername().isBlank()) throw new IllegalArgumentException("A username must not be blank");

		return jdbi.get().inTransaction(handle -> {
			Optional<UsernameEntity> holder = usernameRepository.get()
					.findByUsernameKey(UsernameMapper.usernameKey(change.getNewUsername()))
					.filter(row -> !row.getUniqueId().equals(change.getUniqueId()));
			if (holder.isPresent()) return UsernameWriteResult.taken(holder.get().getUniqueId());

			boolean renamed = usernames.get().update(change.getUniqueId())
					.set("username", change.getNewUsername())
					.set(UsernameEntity.USERNAME_KEY, UsernameMapper.usernameKey(change.getNewUsername()))
					.set("source", change.getSource().getId())
					.set("provider_id", change.getProviderId())
					.set("changed_at", change.getChangedAt())
					.execute();
			if (!renamed) throw new IllegalStateException("Account " + change.getUniqueId() + " has no username");

			history.get().insert(UsernameHistoryMapper.toEntity(change));
			return UsernameWriteResult.written();
		});
	}
}
