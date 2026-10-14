package me.whereareiam.identica.engine.pipeline.scenario.hold;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.common.replication.DefaultReplicationSystem;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.connection.LoginSupersededEvent;
import me.whereareiam.identica.event.connection.UsernameHeldEvent;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.event.scenario.authentication.AuthenticationRequiredEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.pipeline.PendingScenarioService;
import me.whereareiam.identica.replication.ReplicationAdapter;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Two proxies share their replicated state. A player logs in through proxy A while other clients with her username
 * arrive through proxy B.
 */
@DisplayName("Username Hold")
class UsernameHoldTest {
	private static final String OWNER_ADDRESS = "127.0.0.1";
	private static final String OTHER_ADDRESS = "127.0.0.2";

	private final InMemoryAdapter shared = new InMemoryAdapter();
	private final UUID account = UUID.randomUUID();
	private final UUID connection = UUID.randomUUID();
	private final EventManager eventsOfA = mock(EventManager.class);
	private final EventManager eventsOfB = mock(EventManager.class);
	private final IdentityService identitiesOfB = mock(IdentityService.class);
	private final PendingScenarioService pendingOfB = mock(PendingScenarioService.class);
	private UsernameHolds holdsOfA;
	private UsernameHold holdOfB;

	@BeforeAll
	static void initializeSerializer() {
		Serializer.initialize(() -> TEST_SERIALIZER);
	}

	private static final SerializerEngine TEST_SERIALIZER = new SerializerEngine() {
		@Override
		public @NotNull String serialize(Component component) {
			return component.toString();
		}

		@Override
		public @NotNull Component serialize(SerializerContent content) {
			return Component.text(content.getMessage());
		}

		public @NotNull String renderTemplate(@NotNull SerializerContent content) {
			return content.getMessage();
		}

		@Override
		public @NotNull SerializerOptions.PlaceholderFormat getPlaceholderFormat() {
			return SerializerOptions.PlaceholderFormat.CURLY_BRACES;
		}
	};

	@BeforeEach
	void proxies() {
		holdsOfA = new UsernameHolds(new DefaultReplicationSystem(shared), () -> replication("proxy-a"), eventsOfA);
		UsernameHolds holdsOfB = new UsernameHolds(new DefaultReplicationSystem(shared), () -> replication("proxy-b"), eventsOfB);
		holdOfB = new UsernameHold(
				holdsOfB,
				identitiesOfB,
				pendingOfB,
				eventsOfB,
				UsernameHoldTest::messages,
				() -> replication("proxy-b")
		);
	}

	@DisplayName("Lets a connection in while nobody logs in under its username")
	@Test
	void letsAConnectionWithAFreeUsernameIn() {
		assertNull(holdOfB.enter(request(OTHER_ADDRESS, ProviderSelection.empty())));
	}

	@DisplayName("Refuses a connection from another address while a connection of another proxy logs in under the username")
	@Test
	void refusesAnotherAddressWhileTheUsernameIsHeld() {
		ownerWaitsOnProxyA();

		ConnectionDecision decision = holdOfB.enter(request(OTHER_ADDRESS, ProviderSelection.empty()));

		assertEquals(ConnectionDecision.Status.DENY, decision.getStatus());
		assertEquals("held", decision.getMessage());
		ArgumentCaptor<Event> fired = ArgumentCaptor.forClass(Event.class);
		verify(eventsOfB).call(fired.capture());
		UsernameHeldEvent refused = assertInstanceOf(UsernameHeldEvent.class, fired.getValue());
		assertEquals(OTHER_ADDRESS, refused.getIp());
	}

	@DisplayName("Lets the same player in from the address she logs in from")
	@Test
	void letsTheSameAddressIn() {
		ownerWaitsOnProxyA();

		assertNull(holdOfB.enter(request(OWNER_ADDRESS, ProviderSelection.empty())));
	}

	@DisplayName("Lets a connection in again once the holder has left")
	@Test
	void letsAConnectionInOnceTheHolderLeft() {
		ownerWaitsOnProxyA();
		holdsOfA.onIdentityDetached(new IdentityDetachedEvent(connection));

		assertNull(holdOfB.enter(request(OTHER_ADDRESS, ProviderSelection.empty())));
	}

	@DisplayName("Lets a connection a provider proved in, and makes it win the login of the account")
	@Test
	void letsAProvenConnectionInAndSupersedesTheHolder() {
		ownerWaitsOnProxyA();
		ProviderSelection proven = ProviderSelection.of(ProviderClaim.verified("alpha", "subject"), List.of(), List.of());

		assertNull(holdOfB.enter(request(OTHER_ADDRESS, proven)));

		ArgumentCaptor<Event> fired = ArgumentCaptor.forClass(Event.class);
		verify(eventsOfB).call(fired.capture());
		LoginSupersededEvent superseded = assertInstanceOf(LoginSupersededEvent.class, fired.getValue());
		assertEquals(account, superseded.getAccountUniqueId());
		assertEquals("proxy-b", superseded.getWinner().getServerId());
	}

	@DisplayName("A claim without proof never lets a connection past a held username")
	@Test
	void anUnprovenClaimIsRefused() {
		ownerWaitsOnProxyA();
		ProviderSelection hostname = ProviderSelection.of(ProviderClaim.of("alpha", ProviderClaimOrigin.HOSTNAME), List.of(), List.of());

		assertEquals(ConnectionDecision.Status.DENY, holdOfB.enter(request(OTHER_ADDRESS, hostname)).getStatus());
	}

	@DisplayName("Disconnects the connections still logging in when another connection won, but neither the winner nor players already logged in")
	@Test
	void disconnectsTheConnectionsTheWinnerSuperseded() {
		Identity waiting = identity(UUID.randomUUID(), "Alice");
		Identity loggedIn = identity(UUID.randomUUID(), "Alice");
		Identity winner = identity(connection, "Alice");
		when(identitiesOfB.list()).thenReturn(List.of(waiting, loggedIn, winner));
		when(pendingOfB.find(waiting.getConnectionUniqueId())).thenReturn(Optional.of(PipelineType.AUTHENTICATION));
		when(pendingOfB.find(loggedIn.getConnectionUniqueId())).thenReturn(Optional.empty());
		when(pendingOfB.find(connection)).thenReturn(Optional.of(PipelineType.AUTHENTICATION));

		holdOfB.onLoginSuperseded(new LoginSupersededEvent(account, "Alice", new SessionConnection("proxy-b", connection)));

		verify(waiting).disconnect(any());
		verify(loggedIn, never()).disconnect(any());
		verify(winner, never()).disconnect(any());
	}

	@DisplayName("Disconnects a connection of another proxy's winner that uses the same unique id")
	@Test
	void disconnectsTheSameUniqueIdOfAnotherProxy() {
		Identity waiting = identity(connection, "Alice");
		when(identitiesOfB.list()).thenReturn(List.of(waiting));
		when(pendingOfB.find(connection)).thenReturn(Optional.of(PipelineType.AUTHENTICATION));

		holdOfB.onLoginSuperseded(new LoginSupersededEvent(account, "Alice", new SessionConnection("proxy-a", connection)));

		verify(waiting).disconnect(any());
	}

	private void ownerWaitsOnProxyA() {
		ConnectionIdentity identity = new ConnectionIdentity(account, "Alice", OWNER_ADDRESS);
		identity.setConnectionUniqueId(connection);
		AuthContext context = AuthContext.builder()
				.connectionUniqueId(connection)
				.identity(identity)
				.build();
		holdsOfA.onScenarioRequired(new AuthenticationRequiredEvent(context, false, System.currentTimeMillis() + 60_000L, null));
	}

	private ConnectionRequest request(String ip, ProviderSelection selection) {
		ConnectionIdentity identity = new ConnectionIdentity(account, "Alice", ip);
		UUID arriving = UUID.randomUUID();
		identity.setConnectionUniqueId(arriving);
		return ConnectionRequest.builder()
				.connectionUniqueId(arriving)
				.identity(identity)
				.selection(selection)
				.build();
	}

	private Identity identity(UUID connectionUniqueId, String username) {
		Identity identity = mock(Identity.class);
		when(identity.getConnectionUniqueId()).thenReturn(connectionUniqueId);
		when(identity.getAccountUniqueId()).thenReturn(account);
		when(identity.getUsername()).thenReturn(username);
		when(identity.getConnectionUsername()).thenReturn(username);
		return identity;
	}

	private static Messages messages() {
		Messages messages = new Messages();
		Messages.Engine engine = new Messages.Engine();
		engine.setUsernameHeld(List.of("held"));
		engine.setConcurrentLoginKick(List.of("logged in elsewhere"));
		messages.setEngine(engine);
		return messages;
	}

	private static Replication replication(String serverId) {
		Replication replication = new Replication();
		replication.setServerId(serverId);
		Replication.Cache cache = new Replication.Cache();
		cache.setPipelineState("pipeline-state");
		replication.setCache(cache);
		return replication;
	}

	private static final class InMemoryAdapter implements ReplicationAdapter {
		private final Map<String, byte[]> values = new ConcurrentHashMap<>();

		public boolean isAvailable() { return true; }

		public CompletableFuture<Optional<byte[]>> get(String namespace, String key) {
			return CompletableFuture.completedFuture(Optional.ofNullable(values.get(namespace + ":" + key)));
		}

		public CompletableFuture<Optional<byte[]>> consume(String namespace, String key) {
			return CompletableFuture.completedFuture(Optional.ofNullable(values.remove(namespace + ":" + key)));
		}

		public CompletableFuture<Void> put(String namespace, String key, byte[] value, long ttlMs) {
			values.put(namespace + ":" + key, value);
			return CompletableFuture.completedFuture(null);
		}

		public CompletableFuture<Void> invalidate(String namespace, String key) {
			values.remove(namespace + ":" + key);
			return CompletableFuture.completedFuture(null);
		}

		public CompletableFuture<Void> publish(String channel, byte[] payload) {
			return CompletableFuture.completedFuture(null);
		}

		public void subscribe(String channel, Consumer<byte[]> handler) { }
	}
}
