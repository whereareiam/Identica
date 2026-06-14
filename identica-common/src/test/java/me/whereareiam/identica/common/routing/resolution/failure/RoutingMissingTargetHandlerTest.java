package me.whereareiam.identica.common.routing.resolution.failure;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.common.config.defaults.messages.MessagesDefaults;
import me.whereareiam.identica.event.routing.attempt.RoutingAttemptFinishedEvent;
import me.whereareiam.identica.identity.IdentityAttachment;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.routing.RoutingEndpoint;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptReport;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptState;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.platform.adapter.PlatformRoutingAdapter;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.routing.RoutingAttemptTrigger;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@DisplayName("Routing Missing Target Handler")
class RoutingMissingTargetHandlerTest {
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

	@DisplayName("Missing targets disconnect immediately and clean up routing state")
	@Test
	void missingTargetsDisconnectImmediatelyAndCleanUpRoutingState() {
		Messages messages = new MessagesDefaults().supply(new Messages());
		messages.getScenarios().getAuthentication().getRouting().setMissingServer(List.of(
				"Target server {server} does not exist."
		));
		PipelineStateStore pipelineStateStore = mock(PipelineStateStore.class);
		IdentityService identityService = mock(IdentityService.class);
		SessionService sessionService = mock(SessionService.class);
		PlatformRoutingAdapter platformRoutingAdapter = mock(PlatformRoutingAdapter.class);
		me.whereareiam.identica.common.event.EventController eventController = new me.whereareiam.identica.common.event.EventController();
		new RoutingMissingTargetHandler(
				() -> messages,
				pipelineStateStore,
				identityService,
				sessionService,
				platformRoutingAdapter,
				eventController
		);

		RoutingIntent intent = intent();
		UUID accountUniqueId = UUID.randomUUID();
		IdentityAttachment attachment = IdentityAttachment.builder()
				.connectionUniqueId(intent.getConnectionUniqueId())
				.accountUniqueId(accountUniqueId)
				.username("whereareiam")
				.identity(mock(me.whereareiam.identica.identity.actor.Identity.class))
				.build();

		when(identityService.findAttachmentByConnectionUniqueId(intent.getConnectionUniqueId())).thenReturn(Optional.of(attachment));
		when(sessionService.close(accountUniqueId)).thenReturn(CompletableFuture.completedFuture(null));

		eventController.call(new RoutingAttemptFinishedEvent(
				intent,
				RoutingAttemptReport.failed(
						intent.getConnectionUniqueId(),
						RoutingAttemptTrigger.INITIAL_SERVER,
						intent.getEndpoint().getServer(),
						RoutingAttemptFailureReason.MISSING_SERVER
				)
		));

		verify(pipelineStateStore).clear(argThat(reference ->
				intent.getConnectionUniqueId().equals(reference.getConnectionUniqueId())
						&& reference.getAccountUniqueId() == null
						&& reference.getConnectionKey() == null
		));
		verify(sessionService).close(accountUniqueId);

		ArgumentCaptor<Component> messageCaptor = ArgumentCaptor.forClass(Component.class);
		verify(platformRoutingAdapter).disconnect(org.mockito.ArgumentMatchers.eq(intent.getConnectionUniqueId()), messageCaptor.capture());
		assertEquals("Target server lobby does not exist.", PlainTextComponentSerializer.plainText().serialize(messageCaptor.getValue()));
	}

	private RoutingIntent intent() {
		return new RoutingIntent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				new RoutingEndpoint("lobby"),
				RoutingReason.STEP,
				new RoutingAttemptPolicy(),
				new RoutingAttemptState(),
				PipelineType.AUTHENTICATION,
				null,
				null,
				null,
				System.currentTimeMillis()
		);
	}
}
