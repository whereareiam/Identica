package me.whereareiam.identica.engine.pipeline.scenario.hold;

import me.whereareiam.identica.common.replication.DefaultReplicationSystem;
import me.whereareiam.identica.common.replication.lease.DefaultLeaseKeeper;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.event.scenario.authentication.AuthenticationRequiredEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.scheduler.PeriodicalRunnableTask;
import me.whereareiam.identica.replication.ReplicationAdapter;
import me.whereareiam.identica.service.Scheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Two proxies share their replicated state. Alice logs in through proxy A, and proxy B looks at her username as
 * another client of hers arrives from another address.
 */
@DisplayName("Username Holds")
class UsernameHoldsTest {
	private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(30);
	private static final Duration PIPELINE_TTL = Duration.ofMinutes(5);
	private static final String KEY = "alice";

	private final ExpiringAdapter shared = new ExpiringAdapter();
	private final UUID connection = UUID.randomUUID();
	private Proxy proxyA;
	private Proxy proxyB;

	@BeforeEach
	void proxies() {
		proxyA = new Proxy("proxy-a");
		proxyB = new Proxy("proxy-b");
	}

	@DisplayName("A hold is written for the heartbeat timeout, not for the whole login")
	@Test
	void holdLivesForTheHeartbeatTimeout() {
		proxyA.waits(connection, "127.0.0.1");

		assertEquals(HEARTBEAT_TIMEOUT.toMillis(), shared.ttlOf(KEY));
	}

	@DisplayName("The proxy of a waiting login renews its hold, so the hold outlives the heartbeat timeout")
	@Test
	void renewedHoldOutlivesTheHeartbeatTimeout() {
		proxyA.waits(connection, "127.0.0.1");

		for (int renewal = 0; renewal < 6; renewal++) {
			shared.advance(HEARTBEAT_TIMEOUT.dividedBy(3));
			proxyA.renew();
		}

		assertTrue(proxyB.seesHolder());
	}

	@DisplayName("The hold of a proxy that crashed expires once the heartbeat timeout has passed")
	@Test
	void crashedProxyHoldExpires() {
		proxyA.waits(connection, "127.0.0.1");

		shared.advance(HEARTBEAT_TIMEOUT.minusSeconds(1));
		assertTrue(proxyB.seesHolder());

		shared.advance(Duration.ofSeconds(1));
		assertFalse(proxyB.seesHolder());
	}

	@DisplayName("A hold ends when its player leaves and is not written again")
	@Test
	void leavingEndsTheHold() {
		proxyA.waits(connection, "127.0.0.1");

		proxyA.holds.onIdentityDetached(new IdentityDetachedEvent(connection));
		proxyA.renew();

		assertFalse(proxyB.seesHolder());
	}

	@DisplayName("A hold another connection took over is no longer renewed by the proxy that had it")
	@Test
	void takenOverHoldIsNotRenewed() {
		proxyA.waits(connection, "127.0.0.1");
		UUID other = UUID.randomUUID();
		proxyB.waits(other, "127.0.0.3");

		proxyA.renew();
		proxyB.holds.onIdentityDetached(new IdentityDetachedEvent(other));
		proxyA.renew();

		assertFalse(proxyB.seesHolder());
	}

	private static Replication replication(String serverId) {
		Replication replication = new Replication();
		replication.setServerId(serverId);
		replication.setHeartbeatTimeout(HEARTBEAT_TIMEOUT);
		Replication.Cache cache = new Replication.Cache();
		cache.setPipelineState("pipeline-state");
		replication.setCache(cache);
		return replication;
	}

	/**
	 * One proxy of the network, whose lease renewals the test runs itself.
	 */
	private final class Proxy {
		private final UsernameHolds holds;
		private final PeriodicalRunnableTask renewal;

		private Proxy(String serverId) {
			Scheduler scheduler = mock(Scheduler.class);
			DefaultLeaseKeeper leaseKeeper = new DefaultLeaseKeeper(() -> replication(serverId), scheduler);
			holds = new UsernameHolds(new DefaultReplicationSystem(shared), () -> replication(serverId), leaseKeeper, mock(EventManager.class));
			leaseKeeper.initialize();

			ArgumentCaptor<PeriodicalRunnableTask> scheduled = ArgumentCaptor.forClass(PeriodicalRunnableTask.class);
			verify(scheduler).schedule(scheduled.capture());
			renewal = scheduled.getValue();
		}

		private void waits(UUID connectionUniqueId, String ip) {
			ConnectionIdentity identity = new ConnectionIdentity(UUID.randomUUID(), "Alice", ip);
			identity.setConnectionUniqueId(connectionUniqueId);
			AuthContext context = AuthContext.builder()
					.connectionUniqueId(connectionUniqueId)
					.identity(identity)
					.build();
			long expiresAt = System.currentTimeMillis() + PIPELINE_TTL.toMillis();
			holds.onScenarioRequired(new AuthenticationRequiredEvent(context, false, expiresAt, null));
		}

		private void renew() {
			renewal.getRunnable().run();
		}

		/** Whether a client of Alice from yet another address finds her username held. */
		private boolean seesHolder() {
			ConnectionIdentity identity = new ConnectionIdentity(UUID.randomUUID(), "Alice", "127.0.0.2");
			return holds.findHolder(UUID.randomUUID(), identity).isPresent();
		}
	}

	/**
	 * A shared store whose entries expire with their time to live, on a clock the test advances.
	 */
	private static final class ExpiringAdapter implements ReplicationAdapter {
		private final Map<String, Entry> entries = new ConcurrentHashMap<>();
		private long now;

		private void advance(Duration duration) {
			now += duration.toMillis();
		}

		private long ttlOf(String key) {
			return entries.values().stream()
					.filter(entry -> entry.key.equals(key))
					.findFirst()
					.orElseThrow()
					.ttlMs;
		}

		public boolean isAvailable() {
			return true;
		}

		public CompletableFuture<Optional<byte[]>> get(String namespace, String key) {
			Entry entry = entries.get(namespace + ":" + key);
			if (entry == null || entry.expiresAt <= now) return CompletableFuture.completedFuture(Optional.empty());

			return CompletableFuture.completedFuture(Optional.of(entry.value));
		}

		public CompletableFuture<Optional<byte[]>> consume(String namespace, String key) {
			Optional<byte[]> value = get(namespace, key).join();
			entries.remove(namespace + ":" + key);
			return CompletableFuture.completedFuture(value);
		}

		public CompletableFuture<Void> put(String namespace, String key, byte[] value, long ttlMs) {
			entries.put(namespace + ":" + key, new Entry(key, value, ttlMs, now + ttlMs));
			return CompletableFuture.completedFuture(null);
		}

		public CompletableFuture<Void> invalidate(String namespace, String key) {
			entries.remove(namespace + ":" + key);
			return CompletableFuture.completedFuture(null);
		}

		public CompletableFuture<Boolean> compareAndPut(String namespace, String key, byte[] expected, byte[] value, long ttlMs) {
			byte[] current = get(namespace, key).join().orElse(null);
			boolean matches = expected == null ? current == null : current != null && Arrays.equals(current, expected);
			if (matches) put(namespace, key, value, ttlMs);
			return CompletableFuture.completedFuture(matches);
		}

		public CompletableFuture<Boolean> compareAndInvalidate(String namespace, String key, byte[] expected) {
			byte[] current = get(namespace, key).join().orElse(null);
			boolean matches = current != null && Arrays.equals(current, expected);
			if (matches) invalidate(namespace, key);
			return CompletableFuture.completedFuture(matches);
		}

		public CompletableFuture<Void> publish(String channel, byte[] payload) {
			return CompletableFuture.completedFuture(null);
		}

		public void subscribe(String channel, Consumer<byte[]> handler) {
		}

		private record Entry(String key, byte[] value, long ttlMs, long expiresAt) {
		}
	}
}
