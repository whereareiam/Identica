package me.whereareiam.identica.provider.capability.authoritative.username.database.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.provider.capability.authoritative.username.database.AccountUsernameHistoryPersistenceService;
import me.whereareiam.identica.provider.capability.authoritative.username.database.mapper.AuthoritativeUsernameHistoryMapper;
import me.whereareiam.identica.provider.capability.authoritative.username.database.repository.AuthoritativeUsernameHistoryRepository;
import me.whereareiam.identica.provider.capability.authoritative.username.model.account.AccountUsernameHistoryEntry;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;
import me.whereareiam.identica.type.event.EventOrder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@Singleton
public class DefaultAccountUsernameHistoryPersistenceService implements AccountUsernameHistoryPersistenceService, AccountLifecycleParticipant {
	private final AuthoritativeUsernameHistoryRepository repository;

	@Inject
	public DefaultAccountUsernameHistoryPersistenceService(
			@NotNull AuthoritativeUsernameHistoryRepository repository,
			@NotNull Registry<AccountLifecycleParticipant> participants
	) {
		this.repository = repository;
		participants.register(this);
	}

	@Override
	public void record(@NotNull AccountUsernameHistoryEntry entry) {
		if (entry.getOldUsername().isBlank() || entry.getNewUsername().isBlank()) return;

		var entity = AuthoritativeUsernameHistoryMapper.toEntity(entry);
		repository.insert(
				entity.getUniqueId(),
				entity.getProviderId(),
				entity.getOldUsername(),
				entity.getNewUsername(),
				entity.getSource(),
				entity.getChangedAt()
		);
	}

	@Override
	public void deleteAll(@NotNull UUID uniqueId) {
		repository.deleteAll(uniqueId);
	}

	@Override
	public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
		UUID uniqueId = event.getIdentity().getAccountUniqueId();
		if (uniqueId == null) return;

		deleteAll(uniqueId);
	}

	@Override
	public @NotNull EventOrder order() {
		return EventOrder.HIGH;
	}
}
