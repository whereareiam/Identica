package me.whereareiam.identica.common.messaging;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.delivery.DeliveryRequest;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.service.DeliveryStore;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
public class DefaultDeliveryStore implements DeliveryStore {
	private final ReplicationSystem replicationSystem;
	private final Provider<Replication> replicationProvider;
	private final Provider<Engine> engineProvider;
	private final Supplier<Caches> caches = Suppliers.memoize(this::createCaches);

	@Inject
	public DefaultDeliveryStore(
			@NotNull ReplicationSystem replicationSystem,
			@NotNull Provider<Replication> replicationProvider,
			@NotNull Provider<Engine> engineProvider
	) {
		this.replicationSystem = replicationSystem;
		this.replicationProvider = replicationProvider;
		this.engineProvider = engineProvider;
	}

	@Override
	public void put(@NotNull DeliveryRequest request) {
		String requestKey = key(request.getId());
		caches.get().requests().put(requestKey, request, ttlMs()).join();

		UUID connectionUniqueId = request.getTarget().getConnectionUniqueId();
		if (connectionUniqueId != null)
			putIndex(caches.get().connectionIndex(), key(connectionUniqueId), requestKey);

		UUID accountUniqueId = request.getTarget().getAccountUniqueId();
		if (accountUniqueId != null)
			putIndex(caches.get().accountIndex(), key(accountUniqueId), requestKey);
	}

	@Override
	public @NotNull List<DeliveryRequest> findByConnectionUniqueId(@NotNull UUID connectionUniqueId) {
		return resolve(caches.get().connectionIndex(), key(connectionUniqueId));
	}

	@Override
	public @NotNull List<DeliveryRequest> findByAccountUniqueId(@NotNull UUID accountUniqueId) {
		return resolve(caches.get().accountIndex(), key(accountUniqueId));
	}

	@Override
	public @NotNull Optional<DeliveryRequest> findById(@NotNull UUID deliveryUniqueId) {
		return caches.get().requests().get(key(deliveryUniqueId)).join();
	}

	@Override
	public boolean remove(@NotNull UUID deliveryUniqueId) {
		DeliveryRequest request = caches.get().requests().consume(key(deliveryUniqueId)).join().orElse(null);
		if (request == null) return false;

		UUID connectionUniqueId = request.getTarget().getConnectionUniqueId();
		if (connectionUniqueId != null) removeIndex(caches.get().connectionIndex(), key(connectionUniqueId), key(deliveryUniqueId));

		UUID accountUniqueId = request.getTarget().getAccountUniqueId();
		if (accountUniqueId != null) removeIndex(caches.get().accountIndex(), key(accountUniqueId), key(deliveryUniqueId));

		return true;
	}

	private @NotNull List<DeliveryRequest> resolve(
			@NotNull ReplicatedCache<DeliveryRequestKeyIndex> index,
			@NotNull String indexKey
	) {
		List<String> requestKeys = index.get(indexKey).join()
				.map(DeliveryRequestKeyIndex::values)
				.orElse(List.of());
		if (requestKeys.isEmpty()) return List.of();

		List<DeliveryRequest> resolved = new ArrayList<>();
		List<String> retained = new ArrayList<>();
		for (String requestKey : requestKeys) {
			DeliveryRequest request = caches.get().requests().get(requestKey).join().orElse(null);
			if (request == null) continue;

			resolved.add(request);
			retained.add(requestKey);
		}

		if (retained.size() != requestKeys.size())
			index.put(indexKey, new DeliveryRequestKeyIndex(retained), ttlMs()).join();

		return List.copyOf(resolved);
	}

	private void putIndex(
			@NotNull ReplicatedCache<DeliveryRequestKeyIndex> index,
			@NotNull String indexKey,
			@NotNull String requestKey
	) {
		List<String> values = new ArrayList<>(index.get(indexKey).join()
				.map(DeliveryRequestKeyIndex::values)
				.orElse(List.of()));
		if (!values.contains(requestKey)) values.add(requestKey);

		index.put(indexKey, new DeliveryRequestKeyIndex(values), ttlMs()).join();
	}

	private void removeIndex(
			@NotNull ReplicatedCache<DeliveryRequestKeyIndex> index,
			@NotNull String indexKey,
			@NotNull String requestKey
	) {
		List<String> values = new ArrayList<>(index.get(indexKey).join()
				.map(DeliveryRequestKeyIndex::values)
				.orElse(List.of()));

		values.removeIf(requestKey::equals);
		if (values.isEmpty()) {
			index.invalidate(indexKey).join();
			return;
		}

		index.put(indexKey, new DeliveryRequestKeyIndex(values), ttlMs()).join();
	}

	/**
	 * Creates the caches on first use, from the replication document, which is loaded by then.
	 */
	private @NotNull Caches createCaches() {
		Replication.Delivery delivery = replicationProvider.get().getCache().getDelivery();
		var indexType = ReplicationType.identity(DeliveryRequestKeyIndex.class);
		return new Caches(
				replicationSystem.cache(delivery.getRequests()).replicated(ReplicationType.identity(DeliveryRequest.class)),
				replicationSystem.cache(delivery.getConnectionIndex()).replicated(indexType),
				replicationSystem.cache(delivery.getAccountIndex()).replicated(indexType)
		);
	}

	private @NotNull String key(@NotNull UUID uniqueId) {
		return uniqueId.toString();
	}

	private long ttlMs() {
		return engineProvider.get().getBehavior().bridgeTtlMillis();
	}

	private record Caches(
			@NotNull ReplicatedCache<DeliveryRequest> requests,
			@NotNull ReplicatedCache<DeliveryRequestKeyIndex> connectionIndex,
			@NotNull ReplicatedCache<DeliveryRequestKeyIndex> accountIndex
	) {
	}

	private record DeliveryRequestKeyIndex(@NotNull List<String> values) {
		private DeliveryRequestKeyIndex {
			values = List.copyOf(values);
		}
	}
}
