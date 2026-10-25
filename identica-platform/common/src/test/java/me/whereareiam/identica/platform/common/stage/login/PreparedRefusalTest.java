package me.whereareiam.identica.platform.common.stage.login;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.platform.common.stage.InMemoryPrepareStateStore;
import me.whereareiam.identica.platform.common.stage.PrepareDenialMessages;
import me.whereareiam.identica.platform.common.stage.TextSerializerEngine;
import me.whereareiam.keystone.model.SerializerContent;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Prepared Refusal")
class PreparedRefusalTest {
	private static final String CONNECTION_KEY = "Bob|127.0.0.1|play.example.com|25565";

	private final UUID uniqueId = UUID.randomUUID();
	private final InMemoryPrepareStateStore store = new InMemoryPrepareStateStore();
	private final PreparedRefusal refusal = new PreparedRefusal(store, PrepareDenialMessages::messages, messageService());

	@BeforeAll
	static void initializeSerializer() {
		Serializer.initialize(TextSerializerEngine::new);
	}

	@DisplayName("Finds a denial stored by unique ID and forgets it under both keys")
	@Test
	void findsDenialByUniqueId() {
		store.put(uniqueId, CONNECTION_KEY, PrepareDecision.deny(message("restricted")));

		assertEquals(Optional.of(Component.text("restricted")), refusal.find(uniqueId, CONNECTION_KEY));
		assertTrue(store.byUniqueId.isEmpty());
		assertTrue(store.byConnectionKey.isEmpty());
		assertEquals(Optional.empty(), refusal.find(uniqueId, CONNECTION_KEY));
	}

	@DisplayName("Finds a denial by connection key when the unique ID has none")
	@Test
	void findsDenialByConnectionKey() {
		store.put(UUID.randomUUID(), CONNECTION_KEY, PrepareDecision.deny(message("restricted")));

		assertEquals(Optional.of(Component.text("restricted")), refusal.find(uniqueId, CONNECTION_KEY));
		assertTrue(store.byConnectionKey.isEmpty());
	}

	@DisplayName("Refuses a denial without text with the prepare denial message")
	@Test
	void denialWithoutTextUsesPrepareDenialMessage() {
		store.put(uniqueId, CONNECTION_KEY, PrepareDecision.deny(message(" ")));

		assertEquals(Optional.of(Component.text("refused\ncontact us")), refusal.find(uniqueId, CONNECTION_KEY));
	}

	@DisplayName("Does not refuse an allowed decision and keeps it for the login")
	@Test
	void allowedDecisionIsNoRefusal() {
		PrepareDecision allowed = PrepareDecision.allow();
		store.put(uniqueId, CONNECTION_KEY, allowed);

		assertEquals(Optional.empty(), refusal.find(uniqueId, CONNECTION_KEY));
		assertEquals(Optional.of(allowed), store.peek(uniqueId));
		assertEquals(Optional.of(allowed), store.peek(CONNECTION_KEY));
	}

	@DisplayName("Does not refuse a connection nothing was stored for")
	@Test
	void nothingStoredIsNoRefusal() {
		assertEquals(Optional.empty(), refusal.find(uniqueId, CONNECTION_KEY));
		assertEquals(Optional.empty(), refusal.find(uniqueId, null));
	}

	private static PlayerMessage message(String text) {
		return PlayerMessage.builder().text(text).build();
	}

	private static MessageService messageService() {
		MessageService messageService = mock(MessageService.class);
		when(messageService.disconnectReason(any(), any())).thenAnswer(call -> {
			PlayerMessage message = call.getArgument(1);
			return Serializer.serialize(SerializerContent.builder()
					.message(message.getText())
					.placeholders(message.getPlaceholders())
					.build());
		});

		return messageService;
	}

}
