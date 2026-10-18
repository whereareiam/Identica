package me.whereareiam.identica.platform.common.checkpoint;

import me.whereareiam.identica.model.delivery.DeliveryRequest;
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

@DisplayName("Loading Screen Replies")
class LoadingScreenRepliesTest {
	private static final UUID ALICE = UUID.nameUUIDFromBytes("Alice".getBytes());

	private final DeliveryService deliveryService = mock(DeliveryService.class);
	private final LoadingScreenReplies replies = new LoadingScreenReplies(deliveryService);

	@DisplayName("Defers a reply to a held player until their client joined a server")
	@Test
	void defersRepliesWhileHeld() {
		replies.held(ALICE);

		assertTrue(replies.defer(ALICE, "<red>Wrong password"));

		ArgumentCaptor<DeliveryRequest> queued = ArgumentCaptor.forClass(DeliveryRequest.class);
		verify(deliveryService).queue(queued.capture());
		assertEquals(DeliverySource.NOTICE, queued.getValue().getSource());
		assertEquals(DeliverySemantics.ONCE, queued.getValue().getSemantics());
		assertEquals(ALICE, queued.getValue().getTarget().getConnectionUniqueId());
		assertEquals("<red>Wrong password", queued.getValue().getPayload().getChatMessage());
	}

	@DisplayName("Sends replies directly to a player who is not held, or no longer")
	@Test
	void leavesRepliesToOthersAlone() {
		assertFalse(replies.defer(ALICE, "Welcome"));

		replies.held(ALICE);
		replies.released(ALICE);

		assertFalse(replies.defer(ALICE, "Welcome"));
		verify(deliveryService, never()).queue(any());
	}
}
