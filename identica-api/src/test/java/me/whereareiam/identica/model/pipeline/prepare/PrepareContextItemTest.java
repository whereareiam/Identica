package me.whereareiam.identica.model.pipeline.prepare;

import me.whereareiam.identica.model.HandshakeAttributeKey;
import me.whereareiam.identica.model.auth.handshake.HandshakeDecision;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.pipeline.state.PipelineState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@DisplayName("Prepare Context Item")
class PrepareContextItemTest {
	private static final HandshakeAttributeKey<Boolean> FORCE_ONLINE = HandshakeAttributeKey.bool("test:force-online");

	@DisplayName("Keeps the handshake decision and preferred link through the pipeline state")
	@Test
	void keepsTheHandshakeThroughPipelineState() {
		AccountProviderLink link = AccountProviderLink.builder()
				.uniqueId(UUID.randomUUID())
				.providerId("premium")
				.providerSubject("premium-subject")
				.primaryLink(true)
				.build();
		PrepareContextItem context = new PrepareContextItem();
		context.setPreferredLink(link);
		context.setHandshake(HandshakeDecision.deny("Refused").withAttribute(FORCE_ONLINE, true));
		PipelineState state = PipelineState.initial();

		state.putItem(context, 0L);
		PrepareContextItem restored = state.item(PrepareContextItem.class).orElse(null);

		assertNotNull(restored);
		assertNotNull(restored.getHandshake());
		assertEquals(HandshakeDecision.Status.DENY, restored.getHandshake().getStatus());
		assertEquals("Refused", restored.getHandshake().getMessage());
		assertEquals(Optional.of(true), restored.getHandshake().getAttribute(FORCE_ONLINE));
		assertEquals("premium-subject", restored.getPreferredLink().getProviderSubject());
	}

	@DisplayName("Reads a handshake decision stored without attributes as one without attributes")
	@Test
	void readsAHandshakeWithoutAttributes() {
		PrepareContextItem context = new PrepareContextItem();
		context.setHandshake(HandshakeDecision.allow());
		PipelineState state = PipelineState.initial();

		state.putItem(context, 0L);
		PrepareContextItem restored = state.item(PrepareContextItem.class).orElseThrow();

		assertEquals(HandshakeDecision.Status.ALLOW, restored.getHandshake().getStatus());
		assertEquals(0, restored.getHandshake().getAttributes().size());
	}
}
