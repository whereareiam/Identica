package me.whereareiam.identica.adapter.database.username;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.entity.username.UsernameHistoryEntity;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.persistence.username.AccountUsernameHistoryPersistenceService;
import me.whereareiam.identica.adapter.database.mapper.username.UsernameHistoryMapper;
import me.whereareiam.identica.adapter.database.repository.username.UsernameHistoryRepository;
import me.whereareiam.identica.model.identity.username.AccountUsernameHistoryEntry;
import me.whereareiam.identica.type.event.EventOrder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultAccountUsernameHistoryPersistenceService implements AccountUsernameHistoryPersistenceService, EventListener {
	private final Provider<EntityAccess<UsernameHistoryEntity>> history;
	private final Provider<UsernameHistoryRepository> repository;
	private final EventManager eventManager;

	@Inject
	void registerListeners() {
		eventManager.register(this);
	}

	@Override
	public void record(@NotNull AccountUsernameHistoryEntry entry) {
		if (entry.getOldUsername().isBlank() || entry.getNewUsername().isBlank()) return;

		history.get().insert(UsernameHistoryMapper.toEntity(entry));
	}

	@Override
	public void deleteAll(@NotNull UUID uniqueId) {
		repository.get().deleteAll(uniqueId);
	}

	@IdenticEvent(EventOrder.HIGH)
	public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
		UUID uniqueId = event.getIdentity().getAccountUniqueId();
		if (uniqueId == null) return;

		deleteAll(uniqueId);
	}
}
