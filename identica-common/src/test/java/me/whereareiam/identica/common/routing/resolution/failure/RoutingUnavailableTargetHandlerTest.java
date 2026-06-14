package me.whereareiam.identica.common.routing.resolution.failure;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.common.config.defaults.messages.MessagesDefaults;
import me.whereareiam.identica.connection.ConnectionLifecycleService;
import me.whereareiam.identica.event.routing.intent.RoutingIntentExhaustedEvent;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.routing.RoutingEndpoint;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptFailure;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptState;
import me.whereareiam.identica.platform.adapter.PlatformRoutingAdapter;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.routing.reason.RoutingAttemptFailureReason;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Routing Unavailable Target Handler")
class RoutingUnavailableTargetHandlerTest {
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

		@Override
		public @NotNull String renderTemplate(@NotNull SerializerContent content) {
			String rendered = content.getMessage();
			for (var entry : content.getPlaceholders().entrySet())
				rendered = rendered.replace("{" + entry.getKey() + "}", entry.getValue());

			return rendered;
		}

		@Override
		public @NotNull SerializerOptions.PlaceholderFormat getPlaceholderFormat() {
			return SerializerOptions.PlaceholderFormat.CURLY_BRACES;
		}
	};

	@DisplayName("Exhausted unavailable routes use the fallback reason when no detail exists")
	@Test
	void exhaustedUnavailableRoutesUseTheFallbackReasonWhenNoDetailExists() {
		Messages messages = new MessagesDefaults().supply(new Messages());
		messages.getScenarios().getMigration().getRouting().getUnavailableServer().setMessage(List.of(
				"Target server {server} is currently unavailable.",
				"{serverReason}"
		));
		messages.getScenarios().getMigration().getRouting().getUnavailableServer().setFallbackReason(List.of(
				"No details provided."
		));
		PlatformRoutingAdapter platformRoutingAdapter = mock(PlatformRoutingAdapter.class);
		ConnectionLifecycleService connectionLifecycleService = mock(ConnectionLifecycleService.class);
		me.whereareiam.identica.common.event.EventController eventController = new me.whereareiam.identica.common.event.EventController();
		new RoutingUnavailableTargetHandler(
				() -> messages,
				mock(me.whereareiam.identica.identity.IdentityService.class),
				platformRoutingAdapter,
				connectionLifecycleService,
				eventController
		);

		RoutingIntent intent = intent(PipelineType.MIGRATION, "lobby");
		intent.getAttemptState().setLastFailure(new RoutingAttemptFailure(
				RoutingAttemptFailureReason.CONNECTION_RESULT_MISSING,
				null
		));

		eventController.call(new RoutingIntentExhaustedEvent(intent));

		verify(connectionLifecycleService).terminated(
				intent.getConnectionUniqueId(),
				null,
				PipelineType.MIGRATION
		);
		ArgumentCaptor<Component> messageCaptor = ArgumentCaptor.forClass(Component.class);
		verify(platformRoutingAdapter).disconnect(org.mockito.ArgumentMatchers.eq(intent.getConnectionUniqueId()), messageCaptor.capture());
		assertEquals(
				"Target server lobby is currently unavailable.\nNo details provided.",
				PlainTextComponentSerializer.plainText().serialize(messageCaptor.getValue())
		);
	}

	private RoutingIntent intent(PipelineType pipelineType, String targetServer) {
		return new RoutingIntent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				new RoutingEndpoint(targetServer),
				RoutingReason.STEP,
				new RoutingAttemptPolicy(),
				new RoutingAttemptState(),
				pipelineType,
				null,
				null,
				null,
				System.currentTimeMillis()
		);
	}
}
