package me.whereareiam.identica.common.messaging;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.delivery.DeliveryDispatchContext;
import me.whereareiam.identica.model.delivery.DeliveryPayload;
import me.whereareiam.identica.model.delivery.DeliveryRequest;
import me.whereareiam.identica.model.delivery.DeliveryTarget;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.service.DeliveryStore;
import me.whereareiam.identica.type.messaging.DeliverySemantics;
import me.whereareiam.identica.type.messaging.DeliverySource;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Default Delivery Service")
class DefaultDeliveryServiceTest {
	@DisplayName("Dispatch waits for the required server before releasing completion payloads")
	@Test
	void dispatchWaitsForRequiredServer() {
		DeliveryStore deliveryStore = mock(DeliveryStore.class);
		DefaultDeliveryService service = new DefaultDeliveryService(
				deliveryStore,
				mock(PipelineStateStore.class),
				new DefaultMessageService(() -> (recipient, message) -> false, () -> null, mock(EventManager.class))
		);
		TestIdentity identity = new TestIdentity(UUID.randomUUID(), UUID.randomUUID(), "PlayerOne");
		DeliveryRequest request = DeliveryRequest.builder()
				.id(UUID.randomUUID())
				.source(DeliverySource.COMPLETION)
				.target(DeliveryTarget.builder()
						.connectionUniqueId(identity.getConnectionUniqueId())
						.build())
				.payload(DeliveryPayload.builder()
						.completion(DeliveryPayload.CompletionPayload.builder()
								.connectionUniqueId(identity.getConnectionUniqueId())
								.accountUniqueId(identity.getAccountUniqueId())
								.build())
						.build())
				.semantics(DeliverySemantics.ONCE)
				.requiredServer("limbo")
				.createdAt(System.currentTimeMillis())
				.updatedAt(System.currentTimeMillis())
				.build();

		when(deliveryStore.findByConnectionUniqueId(identity.getConnectionUniqueId())).thenReturn(List.of(request));
		when(deliveryStore.findByAccountUniqueId(identity.getAccountUniqueId())).thenReturn(List.of());

		List<DeliveryRequest> wrongServer = service.dispatch(DeliveryDispatchContext.builder()
				.identity(identity)
				.server("hub")
				.build());
		List<DeliveryRequest> rightServer = service.dispatch(DeliveryDispatchContext.builder()
				.identity(identity)
				.server("limbo")
				.build());

		assertEquals(0, wrongServer.size());
		assertEquals(1, rightServer.size());
		assertEquals(request.getId(), rightServer.getFirst().getId());
	}

	private static final class TestIdentity extends Identity {
		private final Audience audience = Audience.empty();

		private TestIdentity(
				@NotNull UUID connectionUniqueId,
				@NotNull UUID accountUniqueId,
				@NotNull String username
		) {
			super(connectionUniqueId, accountUniqueId, username, "127.0.0.1");
		}

		@Override
		public void sendMessage(@NotNull Component message) {
		}

		@Override
		public void sendTitle(@NotNull Title title) {
		}

		@Override
		public boolean hasPermission(@NotNull String permission) {
			return false;
		}

		@Override
		public @NotNull Locale getLocale() {
			return Locale.ENGLISH;
		}

		@Override
		public @NotNull Audience getAudience() {
			return audience;
		}

		@Override
		public void disconnect(@NotNull Component reason) {
		}
	}
}
