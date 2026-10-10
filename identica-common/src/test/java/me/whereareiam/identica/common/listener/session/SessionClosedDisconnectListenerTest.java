package me.whereareiam.identica.common.listener.session;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.identity.session.SessionClosedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.SessionCloseRequest;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.replication.codec.SnapshotCodec;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Session Closed Disconnect Listener")
class SessionClosedDisconnectListenerTest {
	private final UUID uniqueId = UUID.randomUUID();
	private final UUID connectionUniqueId = UUID.randomUUID();
	private final Identity identity = mock(Identity.class);
	private final Identity connection = mock(Identity.class);
	private SessionClosedDisconnectListener listener;

	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.serialize(any(SerializerContent.class)))
				.thenAnswer(call -> Component.text(call.getArgument(0, SerializerContent.class).getMessage()));
		Serializer.initialize(() -> engine);
	}

	@BeforeEach
	void connectedPlayer() {
		IdentityService identities = mock(IdentityService.class);
		when(identities.findByAccountUniqueId(uniqueId)).thenReturn(Optional.of(identity));
		when(identities.findByConnectionUniqueId(connectionUniqueId)).thenReturn(Optional.of(connection));
		Replication replication = new Replication();
		replication.setServerId("proxy-a");
		listener = new SessionClosedDisconnectListener(identities, () -> replication, mock(EventManager.class));
	}

	@DisplayName("A close that asks for a disconnect disconnects with or without a message")
	@ParameterizedTest(name = "message \"{0}\"")
	@NullSource
	@ValueSource(strings = {"", "Your session was ended"})
	void disconnectsWhenAskedWithOrWithoutMessage(String message) {
		listener.onSessionClosed(closed(true, message));

		verify(identity).disconnect(Component.text(message == null ? "" : message));
	}

	@DisplayName("A close that does not ask for a disconnect leaves the player connected, even with a message")
	@Test
	void keepsPlayerConnectedWhenNotAsked() {
		listener.onSessionClosed(closed(false, "Your session was ended"));

		verify(identity, never()).disconnect(any());
	}

	@DisplayName("The disconnect decision reaches another proxy without a message")
	@Test
	void disconnectDecisionSurvivesReplication() {
		SnapshotCodec<SessionClosedEvent> codec = SnapshotCodec.json(SessionClosedEvent.class);

		SessionClosedEvent received = codec.decode(codec.encode(closed(true, "")));

		assertTrue(received.getRequest().isDisconnect());
		listener.onSessionClosed(received);
		verify(identity).disconnect(Component.text(""));
		assertFalse(codec.decode(codec.encode(closed(false, "text"))).getRequest().isDisconnect());
	}

	@DisplayName("A close naming a connection disconnects only that connection, on the proxy that holds it")
	@Test
	void disconnectsOnlyTheNamedConnectionOnItsProxy() {
		listener.onSessionClosed(closedConnection("proxy-a"));

		verify(connection).disconnect(Component.text("Logged in from another location"));
		verify(identity, never()).disconnect(any());
	}

	@DisplayName("A close naming a connection on another proxy disconnects nobody here")
	@Test
	void leavesPlayersOfThisProxyWhenTheConnectionIsElsewhere() {
		listener.onSessionClosed(closedConnection("proxy-b"));

		verify(connection, never()).disconnect(any());
		verify(identity, never()).disconnect(any());
	}

	@DisplayName("The named connection reaches another proxy through replication")
	@Test
	void namedConnectionSurvivesReplication() {
		SnapshotCodec<SessionClosedEvent> codec = SnapshotCodec.json(SessionClosedEvent.class);

		SessionClosedEvent received = codec.decode(codec.encode(closedConnection("proxy-a")));

		assertEquals("proxy-a", received.getRequest().getConnection().getServerId());
		assertEquals(connectionUniqueId, received.getRequest().getConnection().getConnectionUniqueId());
		assertEquals(connectionUniqueId, received.getSession().getConnection().getConnectionUniqueId());
		listener.onSessionClosed(received);
		verify(connection).disconnect(Component.text("Logged in from another location"));
	}

	private SessionClosedEvent closedConnection(String serverId) {
		SessionConnection held = new SessionConnection(serverId, connectionUniqueId);
		Session session = Session.builder()
				.sessionId(UUID.randomUUID().toString())
				.uniqueId(uniqueId)
				.connection(held)
				.build();
		return new SessionClosedEvent(uniqueId, session, SessionCloseRequest.builder()
				.requestId(UUID.randomUUID())
				.uniqueId(uniqueId)
				.connection(held)
				.disconnect(true)
				.disconnectMessage("Logged in from another location")
				.build());
	}

	private SessionClosedEvent closed(boolean disconnect, String message) {
		return new SessionClosedEvent(uniqueId, null, SessionCloseRequest.builder()
				.requestId(UUID.randomUUID())
				.uniqueId(uniqueId)
				.disconnect(disconnect)
				.disconnectMessage(message)
				.build());
	}
}
