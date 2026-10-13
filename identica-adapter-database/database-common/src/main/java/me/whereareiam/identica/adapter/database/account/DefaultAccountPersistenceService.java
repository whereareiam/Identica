package me.whereareiam.identica.adapter.database.account;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import me.whereareiam.identica.adapter.database.entity.username.UsernameEntity;
import me.whereareiam.identica.adapter.database.mapper.account.AccountMapper;
import me.whereareiam.identica.adapter.database.mapper.username.UsernameMapper;
import me.whereareiam.identica.adapter.database.repository.account.AccountRepository;
import me.whereareiam.identica.adapter.database.repository.username.UsernameHistoryRepository;
import me.whereareiam.identica.adapter.database.repository.username.UsernameRepository;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.username.UsernameWriteResult;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.type.event.EventOrder;
import org.jdbi.v3.core.Jdbi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Keeps accounts in {@code identica_accounts} and their usernames in {@code identica_account_usernames}: an account
 * is read with its username, and created and deleted with it in one transaction.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultAccountPersistenceService implements AccountPersistenceService, EventListener {
	private final Provider<Jdbi> jdbi;
	private final Provider<EntityAccess<AccountEntity>> accounts;
	private final Provider<EntityAccess<UsernameEntity>> usernames;
	private final Provider<AccountRepository> accountRepository;
	private final Provider<UsernameRepository> usernameRepository;
	private final Provider<UsernameHistoryRepository> usernameHistoryRepository;
	private final EventManager eventManager;

	@Inject
	void registerListeners() {
		eventManager.register(this);
	}

	@Override
	public @NotNull Optional<Account> findByUniqueId(@NotNull UUID uniqueId) {
		try {
			return usernames.get().find(uniqueId).flatMap(this::account);
		} catch (Exception e) {
			Logger.warn(e, "Failed to load account %s", uniqueId);
			return Optional.empty();
		}
	}

	@Override
	public @NotNull List<Account> findByUsername(@NotNull String username) {
		if (username.isBlank()) return List.of();

		try {
			return usernameRepository.get().findByUsername(username).stream()
					.flatMap(row -> account(row).stream())
					.toList();
		} catch (Exception e) {
			Logger.warn(e, "Failed to load accounts by username %s", username);
			return List.of();
		}
	}

	@Override
	public long count() {
		try {
			return accountRepository.get().count();
		} catch (Exception e) {
			Logger.warn(e, "Failed to count accounts");
			return 0L;
		}
	}

	/**
	 * Creates an account with its username in one transaction. When another row holds the username's key, the
	 * transaction is rolled back and the result names that row's account.
	 */
	@Override
	public @NotNull UsernameWriteResult create(
			@NotNull Account account,
			@NotNull AccountUsernameSource source,
			@Nullable String providerId
	) {
		UsernameEntity username = UsernameMapper.toEntity(account, source, providerId);
		boolean created = jdbi.get().inTransaction(handle -> {
			accounts.get().insert(AccountMapper.toEntity(account));
			if (usernames.get().insertIfAbsent(username)) return true;

			handle.rollback();
			return false;
		});
		if (created) return UsernameWriteResult.written();

		return usernameRepository.get().findByUsernameKey(username.getUsernameKey())
				.map(holder -> UsernameWriteResult.taken(holder.getUniqueId()))
				.orElseThrow(() -> new IllegalStateException("Account " + account.getUniqueId() + " could not take the username " + account.getUsername()));
	}

	@Override
	public void updateLastSeen(@NotNull UUID uniqueId, long lastSeenAt) {
		try {
			accounts.get().update(uniqueId).set("last_seen_at", lastSeenAt).execute();
		} catch (Exception e) {
			Logger.warn(e, "Failed to update last seen for %s", uniqueId);
		}
	}

	/**
	 * Deletes an account with its username and username history in one transaction. The rows that reference the
	 * account are deleted here as well, because SQLite leaves foreign keys unenforced.
	 */
	@Override
	public void delete(@NotNull UUID uniqueId) {
		try {
			jdbi.get().useTransaction(handle -> {
				usernameHistoryRepository.get().deleteAll(uniqueId);
				usernames.get().delete(uniqueId);
				accounts.get().delete(uniqueId);
			});
		} catch (Exception e) {
			Logger.warn(e, "Failed to delete account %s", uniqueId);
		}
	}

	@IdenticEvent(EventOrder.HIGHEST)
	public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
		UUID uniqueId = event.getIdentity().getAccountUniqueId();
		if (uniqueId == null) return;

		delete(uniqueId);
	}

	private Optional<Account> account(UsernameEntity username) {
		return accounts.get().find(username.getUniqueId()).map(entity -> AccountMapper.toModel(entity, username));
	}
}
