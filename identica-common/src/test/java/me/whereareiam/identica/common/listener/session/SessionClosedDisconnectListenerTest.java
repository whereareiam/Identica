package me.whereareiam.identica.common.listener.session;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.identity.session.SessionClosedEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.SessionCloseRequest;
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
	private final Identity identity = mock(Identity.class);
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
		listener = new SessionClosedDisconnectListener(identities, mock(EventManager.class));
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

	private SessionClosedEvent closed(boolean disconnect, String message) {
		return new SessionClosedEvent(uniqueId, null, SessionCloseRequest.builder()
				.requestId(UUID.randomUUID())
				.uniqueId(uniqueId)
				.disconnect(disconnect)
				.disconnectMessage(message)
				.build());
	}
}
