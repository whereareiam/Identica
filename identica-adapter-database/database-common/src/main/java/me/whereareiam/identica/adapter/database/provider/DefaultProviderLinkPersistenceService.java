package me.whereareiam.identica.adapter.database.provider;

import com.google.inject.Inject;
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
	private final EntityAccess<AccountProviderLinkEntity> links;
	private final ProviderLinkRepository repository;

	@Inject
	public DefaultProviderLinkPersistenceService(
			EntityAccess<AccountProviderLinkEntity> links,
			ProviderLinkRepository repository,
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
		return links.find(providerId, providerSubject).map(AccountProviderLinkMapper::toModel);
	}

	@Override
	public @NotNull Optional<AccountProviderLink> findByUniqueIdAndProviderId(@NotNull UUID uniqueId, @NotNull String providerId) {
		if (providerId.isBlank()) return Optional.empty();
		return repository.findByUniqueIdAndProviderId(uniqueId, providerId).map(AccountProviderLinkMapper::toModel);
	}

	@Override
	public @NotNull List<AccountProviderLink> findByUniqueId(@NotNull UUID uniqueId) {
		return repository.findByUniqueId(uniqueId).stream()
				.map(AccountProviderLinkMapper::toModel)
				.toList();
	}

	@Override
	public @NotNull Map<String, Long> countByProvider() {
		return Collections.unmodifiableMap(repository.countByProvider());
	}

	@Override
	public @NotNull AccountProviderLink upsert(@NotNull AccountProviderLink link) {
		if (link.getProviderId().isBlank())
			throw new IllegalArgumentException("Provider id is required");
		if (link.getProviderSubject().isBlank())
			throw new IllegalArgumentException("Provider subject is required");

		AccountProviderLinkEntity entity = AccountProviderLinkMapper.toEntity(link);
		Optional<AccountProviderLinkEntity> existing = links.find(link.getProviderId(), link.getProviderSubject());
		if (existing.isPresent()) {
			repository.update(link.getProviderId(), link.getProviderSubject(), link.isPrimaryLink(), link.getLastSeenAt());
			if (link.isPrimaryLink()) {
				repository.updatePrimary(link.getUniqueId(), link.getProviderId(), true);
			}
			return AccountProviderLinkMapper.toModel(existing.get().toBuilder()
					.primaryLink(link.isPrimaryLink())
					.lastSeenAt(link.getLastSeenAt())
					.build());
		}

		links.insert(entity);

		if (link.isPrimaryLink()) {
			repository.updatePrimary(link.getUniqueId(), link.getProviderId(), true);
		}

		return AccountProviderLinkMapper.toModel(entity);
	}

	@Override
	public void setPrimaryExclusive(@NotNull UUID uniqueId, @NotNull String providerId) {
		if (providerId.isBlank()) throw new IllegalArgumentException("Provider id is required");
		repository.clearPrimary(uniqueId, false);
		repository.updatePrimary(uniqueId, providerId, true);
	}

	@Override
	public void delete(@NotNull UUID uniqueId, @NotNull String providerId) {
		if (providerId.isBlank()) throw new IllegalArgumentException("Provider id is required");
		repository.delete(uniqueId, providerId);
	}

	@Override
	public void deleteAll(@NotNull UUID uniqueId) {
		repository.deleteAll(uniqueId);
	}

	@IdenticEvent(EventOrder.HIGH)
	public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
		UUID uniqueId = event.getIdentity().getAccountUniqueId();
		if (uniqueId == null) return;

		deleteAll(uniqueId);
	}
}
