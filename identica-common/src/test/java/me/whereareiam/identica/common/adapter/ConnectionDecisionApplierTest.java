package me.whereareiam.identica.common.adapter;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

@DisplayName("Connection Decision Applier")
class ConnectionDecisionApplierTest {
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
			String message = content.getMessage();
			return Component.text(message);
		}

		public @NotNull String renderTemplate(SerializerContent content) {
			return content.getMessage();
		}

		@Override
		public SerializerOptions.PlaceholderFormat getPlaceholderFormat() {
			return SerializerOptions.PlaceholderFormat.CURLY_BRACES;
		}
	};

	@DisplayName("Sends waiting decisions back to the actor as a message")
	@Test
	void waitDecisionSendsActorMessage() {
		ConnectionDecisionApplier applier = new ConnectionDecisionApplier(
				this::messages,
				mock(ConnectionDecisionDeliveryCoordinator.class),
				mock(me.whereareiam.identica.service.PlatformDeliveryAdapter.class)
		);
		TestActor actor = new TestActor();
		TestTarget target = new TestTarget();

		applier.apply(ConnectionDecision.waiting("wait-message"), actor, target);

		assertNotNull(actor.lastMessage.get());
		assertNull(target.denied.get());
		assertNull(target.reconnect.get());
	}

	@DisplayName("Delegates denied decisions to the target")
	@Test
	void denyDecisionUsesTarget() {
		ConnectionDecisionApplier applier = new ConnectionDecisionApplier(
				this::messages,
				mock(ConnectionDecisionDeliveryCoordinator.class),
				mock(me.whereareiam.identica.service.PlatformDeliveryAdapter.class)
		);
		TestActor actor = new TestActor();
		TestTarget target = new TestTarget();

		applier.apply(ConnectionDecision.deny("denied"), actor, target);

		assertNotNull(target.denied.get());
	}

	@DisplayName("Delegates reconnect decisions to the target")
	@Test
	void reconnectDecisionUsesTarget() {
		ConnectionDecisionApplier applier = new ConnectionDecisionApplier(
				this::messages,
				mock(ConnectionDecisionDeliveryCoordinator.class),
				mock(me.whereareiam.identica.service.PlatformDeliveryAdapter.class)
		);
		TestActor actor = new TestActor();
		TestTarget target = new TestTarget();

		applier.apply(ConnectionDecision.requireReconnect("reconnect"), actor, target);

		assertNotNull(target.reconnect.get());
	}

	@DisplayName("Denies and requires a reconnect without text in the decision or in the fallback message")
	@Test
	void denialsAreAppliedWithoutAnyText() {
		ConnectionDecisionApplier applier = new ConnectionDecisionApplier(
				() -> messages(List.of()),
				mock(ConnectionDecisionDeliveryCoordinator.class),
				mock(me.whereareiam.identica.service.PlatformDeliveryAdapter.class)
		);
		TestTarget target = new TestTarget();

		applier.apply(ConnectionDecision.deny(""), new TestActor(), target);
		applier.apply(ConnectionDecision.requireReconnect(null), new TestActor(), target);

		assertNotNull(target.denied.get());
		assertNotNull(target.reconnect.get());
	}

	private Messages messages() {
		return messages(List.of("fallback"));
	}

	private Messages messages(List<String> authenticationFailed) {
		Messages messages = new Messages();
		Messages.Scenarios scenarios = new Messages.Scenarios();
		Messages.Scenarios.Authentication authentication = new Messages.Scenarios.Authentication();
		authentication.setAuthenticationFailed(authenticationFailed);
		scenarios.setAuthentication(authentication);
		messages.setScenarios(scenarios);
		return messages;
	}

	private static final class TestTarget implements ConnectionDecisionApplier.Target {
		private final AtomicReference<Component> denied = new AtomicReference<>();
		private final AtomicReference<Component> reconnect = new AtomicReference<>();

		@Override
		public void deny(@NotNull Component message) {
			denied.set(message);
		}

		@Override
		public void requireReconnect(@NotNull Component message) {
			reconnect.set(message);
		}
	}

	private static final class TestActor implements Actor {
		private final AtomicReference<Component> lastMessage = new AtomicReference<>();

		@Override
		public @NotNull UUID getUniqueId() {
			return UUID.randomUUID();
		}

		@Override
		public @NotNull String getUsername() {
			return "PlayerOne";
		}

		@Override
		public void sendMessage(@NotNull Component message) {
			lastMessage.set(message);
		}

		@Override
		public boolean hasPermission(@NotNull String permission) {
			return true;
		}

		@Override
		public @NotNull Locale getLocale() {
			return Locale.ENGLISH;
		}

		@Override
		public @NotNull Audience getAudience() {
			return Audience.empty();
		}
	}
}
