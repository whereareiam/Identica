package me.whereareiam.identica.adapter.database.provider;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.dialectica.adapter.jdbi.entity.EntityAccess;
import me.whereareiam.identica.adapter.database.entity.account.AccountProviderLinkEntity;
import me.whereareiam.identica.adapter.database.mapper.account.AccountProviderLinkMapper;
import me.whereareiam.identica.adapter.database.repository.provider.ProviderLinkRepository;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.type.event.EventOrder;
import org.jetbrains.annotations.NotNull;

import java.util.*;

@Singleton
public class DefaultProviderLinkPersistenceService implements ProviderLinkPersistenceService, EventListener {
	private final Provider<EntityAccess<AccountProviderLinkEntity>> links;
	private final Provider<ProviderLinkRepository> repository;

	@Inject
	public DefaultProviderLinkPersistenceService(
			Provider<EntityAccess<AccountProviderLinkEntity>> links,
			Provider<ProviderLinkRepository> repository,
			EventManager eventManager
	) {
		this.links = links;
		this.repository = repository;
		eventManager.register(this);
	}

	@Override
	public @NotNull Optional<AccountProviderLink> findBySubject(
			@NotNull String providerId, @NotNull String providerSubject
	) {
		if (providerId.isBlank() || providerSubject.isBlank()) return Optional.empty();
		return links.get().find(providerId, providerSubject).map(AccountProviderLinkMapper::toModel);
	}

	@Override
	public @NotNull Optional<AccountProviderLink> findByUniqueIdAndProviderId(@NotNull UUID uniqueId, @NotNull String providerId) {
		if (providerId.isBlank()) return Optional.empty();
		return repository.get().findByUniqueIdAndProviderId(uniqueId, providerId).map(AccountProviderLinkMapper::toModel);
	}

	@Override
	public @NotNull List<AccountProviderLink> findByUniqueId(@NotNull UUID uniqueId) {
		return repository.get().findByUniqueId(uniqueId).stream()
				.map(AccountProviderLinkMapper::toModel)
				.toList();
	}

	@Override
	public @NotNull Map<String, Long> countByProvider() {
		return Collections.unmodifiableMap(repository.get().countByProvider());
	}

	@Override
	public @NotNull AccountProviderLink upsert(@NotNull AccountProviderLink link) {
		if (link.getProviderId().isBlank())
			throw new IllegalArgumentException("Provider id is required");
		if (link.getProviderSubject().isBlank())
			throw new IllegalArgumentException("Provider subject is required");

		AccountProviderLinkEntity entity = AccountProviderLinkMapper.toEntity(link);
		Optional<AccountProviderLinkEntity> existing = links.get().find(link.getProviderId(), link.getProviderSubject());
		if (existing.isPresent()) {
			repository.get().update(link.getProviderId(), link.getProviderSubject(), link.isPrimaryLink(), link.getLastSeenAt());
			if (link.isPrimaryLink()) {
				repository.get().updatePrimary(link.getUniqueId(), link.getProviderId(), true);
			}
			return AccountProviderLinkMapper.toModel(existing.get().toBuilder()
					.primaryLink(link.isPrimaryLink())
					.lastSeenAt(link.getLastSeenAt())
					.build());
		}

		links.get().insert(entity);

		if (link.isPrimaryLink()) {
			repository.get().updatePrimary(link.getUniqueId(), link.getProviderId(), true);
		}

		return AccountProviderLinkMapper.toModel(entity);
	}

	@Override
	public void setPrimaryExclusive(@NotNull UUID uniqueId, @NotNull String providerId) {
		if (providerId.isBlank()) throw new IllegalArgumentException("Provider id is required");
		repository.get().clearPrimary(uniqueId, false);
		repository.get().updatePrimary(uniqueId, providerId, true);
	}

	@Override
	public void delete(@NotNull UUID uniqueId, @NotNull String providerId) {
		if (providerId.isBlank()) throw new IllegalArgumentException("Provider id is required");
		repository.get().delete(uniqueId, providerId);
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
