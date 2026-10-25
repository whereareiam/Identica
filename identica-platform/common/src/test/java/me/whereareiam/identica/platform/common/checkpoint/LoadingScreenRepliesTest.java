package me.whereareiam.identica.platform.common.checkpoint;

import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.delivery.DeliveryRequest;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.service.DeliveryService;
import me.whereareiam.identica.type.messaging.DeliverySemantics;
import me.whereareiam.identica.type.messaging.DeliverySource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Loading Screen Replies")
class LoadingScreenRepliesTest {
	private static final UUID ALICE = UUID.nameUUIDFromBytes("Alice".getBytes());

	private final DeliveryService deliveryService = mock(DeliveryService.class);
	private final LoadingScreenReplies replies = new LoadingScreenReplies(deliveryService);

	@DisplayName("Defers the chat to a held player until their client joined a server")
	@Test
	void defersRepliesWhileHeld() {
		replies.held(ALICE);

		assertTrue(replies.hold(alice(), message("<red>Wrong password")));

		ArgumentCaptor<DeliveryRequest> queued = ArgumentCaptor.forClass(DeliveryRequest.class);
		verify(deliveryService).queue(queued.capture());
		assertEquals(DeliverySource.NOTICE, queued.getValue().getSource());
		assertEquals(DeliverySemantics.ONCE, queued.getValue().getSemantics());
		assertEquals(ALICE, queued.getValue().getTarget().getConnectionUniqueId());
		assertEquals("<red>Wrong password", queued.getValue().getPayload().getMessage().getText());
		assertTrue(queued.getValue().getPayload().isChatOnly());
	}

	@DisplayName("Sends replies directly to a player who is not held, or no longer")
	@Test
	void leavesRepliesToOthersAlone() {
		assertFalse(replies.hold(alice(), message("Welcome")));

		replies.held(ALICE);
		replies.released(ALICE);

		assertFalse(replies.hold(alice(), message("Welcome")));
		verify(deliveryService, never()).queue(any());
	}

	private static Identity alice() {
		Identity alice = mock(Identity.class);
		when(alice.getConnectionUniqueId()).thenReturn(ALICE);

		return alice;
	}

	private static PlayerMessage message(String text) {
		return PlayerMessage.builder().text(text).build();
	}
}
