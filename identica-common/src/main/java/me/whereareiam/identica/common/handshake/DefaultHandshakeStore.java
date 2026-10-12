package me.whereareiam.identica.common.handshake;

import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.model.replication.ReplicationType;
import me.whereareiam.identica.model.replication.ReplicationPage;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.account.AccountLifecycleEvent;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.handshake.HandshakeInstructionEvent;
import me.whereareiam.identica.handshake.policy.HandshakePolicy;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.handshake.HandshakeInstruction;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.type.event.EventOrder;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

@Singleton
public final class DefaultHandshakeStore implements HandshakeStore, EventListener {
	private static final String KEY_SEPARATOR = "|";
	private final Set<HandshakePolicy> policies = new CopyOnWriteArraySet<>();
	private final Supplier<ReplicatedCache<HandshakeInstruction>> cache;
	private final EventManager eventManager;

	@Inject
	public DefaultHandshakeStore(
			ReplicationSystem replicationSystem,
			Provider<Replication> replicationProvider,
			EventManager eventManager
	) {
		this.cache = Suppliers.memoize(() -> replicationSystem.cache(resolveNamespace(replicationProvider))
				.replicated(ReplicationType.identity(HandshakeInstruction.class)));
		this.eventManager = eventManager;
		eventManager.register(this);
	}

	@Override
	public void registerPolicy(@NotNull HandshakePolicy policy) {
		policies.add(policy);
		Logger.debug("Registered handshake policy %s", policy.getClass().getSimpleName());
	}

	@Override
	public void unregisterPolicy(@NotNull HandshakePolicy policy) {
		policies.remove(policy);
		Logger.debug("Unregistered handshake policy %s", policy.getClass().getSimpleName());
	}

	@Override
	public @NotNull Set<HandshakePolicy> policies() {
		return Collections.unmodifiableSet(policies);
	}

	@Override
	public void putInstruction(@NotNull HandshakeInstruction instruction) {
		HandshakeInstructionEvent event = new HandshakeInstructionEvent(instruction);
		eventManager.call(event);
		if (event.isCancelled()) return;

		HandshakeInstruction stored = event.getInstruction();
		String username = stored.getIdentity().getUsername();
		String ip = stored.getIdentity().getIp();
		if (username.isBlank() || ip == null || ip.isBlank()) return;

		String key = resolveKey(username, ip);
		long ttlMs = Math.max(1, stored.getExpiresAt() - System.currentTimeMillis());
		cache.get().put(key, stored, ttlMs).join();
	}

	@Override
	public @NotNull Optional<HandshakeInstruction> consumeInstruction(@NotNull String username, @NotNull String ip) {
		if (username.isBlank() || ip.isBlank()) return Optional.empty();
		return readByKey(resolveKey(username, ip));
	}

	@Override
	public void invalidateInstruction(@NotNull String username, @NotNull String ip) {
		if (username.isBlank()) return;
		if (ip.isBlank()) {
			invalidateByUsername(username);
			return;
		}

		cache.get().invalidate(resolveKey(username, ip)).join();
	}

	@IdenticEvent(EventOrder.LOWEST)
	public void onAccountLifecycle(@NotNull AccountLifecycleEvent event) {
		String username = event.getIdentity().getUsername();
		if (username.isBlank()) return;
		invalidateInstruction(username, "");
	}

	private Optional<HandshakeInstruction> readByKey(@NotNull String key) {
		HandshakeInstruction instruction = (cache.get().consume(key))
				.join()
				.orElse(null);

		if (instruction == null) return Optional.empty();
		if (instruction.isExpired(System.currentTimeMillis()))
			return Optional.empty();

		return Optional.of(instruction);
	}

	private void invalidateByUsername(@NotNull String username) {
		String prefix = normalize(username) + KEY_SEPARATOR;
		int page = 1;
		int pageSize = 100;

		while (true) {
			ReplicationPage resolved = cache.get().listKeys(page, pageSize).join();
			for (String key : resolved.getEntries()) {
				if (key != null && key.startsWith(prefix))
					cache.get().invalidate(key).join();
			}

			if (resolved.getEntries().isEmpty()) return;
			if (resolved.getTotal() <= page * resolved.getPageSize()) return;

			page++;
		}
	}

	private @NotNull String resolveKey(@NotNull String username, @NotNull String ip) {
		return normalize(username) + KEY_SEPARATOR + normalize(ip);
	}

	private @NotNull String normalize(@NotNull String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}

	private static String resolveNamespace(Provider<Replication> replicationProvider) {
		Replication replication = replicationProvider.get();
		if (replication == null)
			throw new IllegalStateException("replication is missing");

		String namespace = replication.getCache().getInstructions();
		if (namespace.isBlank())
			throw new IllegalStateException("replication.cache.instructions is missing");

		return namespace;
	}
}
