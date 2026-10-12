package me.whereareiam.identica.adapter.database.account;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.entity.account.AccountEntity;
import me.whereareiam.identica.adapter.database.mapper.account.AccountMapper;
import me.whereareiam.identica.adapter.database.repository.account.AccountRepository;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.type.event.EventOrder;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultAccountPersistenceService implements AccountPersistenceService, EventListener {
	private final Provider<EntityAccess<AccountEntity>> accounts;
	private final Provider<AccountRepository> accountRepository;
	private final EventManager eventManager;

	@Inject
	void registerListeners() {
		eventManager.register(this);
	}

	@Override
	public @NotNull Optional<Account> findByUniqueId(@NotNull UUID uniqueId) {
		try {
			return accounts.get().find(uniqueId).map(AccountMapper::toModel);
		} catch (Exception e) {
			Logger.warn(e, "Failed to load account %s", uniqueId);
			return Optional.empty();
		}
	}

	@Override
	public @NotNull List<Account> findByUsername(@NotNull String username) {
		if (username.isBlank()) return List.of();

		try {
			return accountRepository.get().findByUsername(username).stream()
					.map(AccountMapper::toModel)
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

	@Override
	public @NotNull Account create(@NotNull Account account) {
		AccountEntity entity = AccountMapper.toEntity(account);

		try {
			accounts.get().insert(entity);

			return AccountMapper.toModel(entity);
		} catch (Exception e) {
			Logger.warn(e, "Failed to create account %s", account.getUniqueId());
			throw e;
		}
	}

	@Override
	public void updateLastSeen(@NotNull UUID uniqueId, long lastSeenAt) {
		try {
			accounts.get().update(uniqueId).set("last_seen_at", lastSeenAt).execute();
		} catch (Exception e) {
			Logger.warn(e, "Failed to update last seen for %s", uniqueId);
		}
	}

	@Override
	public void updateUsername(@NotNull UUID uniqueId, @NotNull String username) {
		if (username.isBlank()) return;

		try {
			accounts.get().update(uniqueId).set("username", username).execute();
		} catch (Exception e) {
			Logger.warn(e, "Failed to update username for %s", uniqueId);
		}
	}

	@Override
	public void delete(@NotNull UUID uniqueId) {
		try {
			accounts.get().delete(uniqueId);
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
}
