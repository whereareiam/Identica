package me.whereareiam.identica.platform.common.decision;

import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.HandshakeAttributeKey;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.handshake.HandshakeDecision;
import me.whereareiam.identica.model.auth.handshake.HandshakeInstruction;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Prepare Request Connection Keys")
class PrepareRequestConnectionKeyTest {
	@DisplayName("Profile rewrites forward the full connection key into prepare requests")
	@Test
	void profileRewritePassesConnectionKeyIntoPrepareRequest() {
		ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
		when(connectionCoordinator.prepare(any())).thenReturn(CompletableFuture.completedFuture(PrepareDecision.allow()));
		ProfileRewriteProcessor processor = new ProfileRewriteProcessor(connectionCoordinator, new NoopPrepareStateStore());
		ConnectionIdentity identity = identity("PlayerOne");

		processor.process(new ProfileRewriteProcessor.Request(
						identity,
						UUID.randomUUID(),
						identity.getUsername()
				), rewrite -> {
				})
				.toCompletableFuture()
				.join();

		ArgumentCaptor<PrepareRequest> captor = forClass(PrepareRequest.class);
		org.mockito.Mockito.verify(connectionCoordinator).prepare(captor.capture());
		PrepareRequest captured = captor.getValue();
		assertEquals(identity.connectionKey(), captured.getConnectionKey());
	}

	@DisplayName("Handshake preparation forwards the full connection key into prepare requests")
	@Test
	void handshakePassesConnectionKeyIntoPrepareRequest() {
		ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
		when(connectionCoordinator.prepare(any())).thenReturn(CompletableFuture.completedFuture(PrepareDecision.allow()));
		HandshakeStore handshakeStore = mock(HandshakeStore.class);
		when(handshakeStore.consumeInstruction(any(), any())).thenReturn(Optional.empty());

		HandshakeDecisionProcessor processor = new HandshakeDecisionProcessor(
				connectionCoordinator,
				new NoopPrepareStateStore(),
				handshakeStore,
				Messages::new
		);
		ConnectionIdentity identity = identity("PlayerTwo");

		processor.process(new HandshakeDecisionProcessor.Request(
						identity,
						instruction -> {
						}
				), message -> {
				})
				.toCompletableFuture()
				.join();

		ArgumentCaptor<PrepareRequest> captor = forClass(PrepareRequest.class);
		org.mockito.Mockito.verify(connectionCoordinator).prepare(captor.capture());
		PrepareRequest captured = captor.getValue();
		assertEquals(identity.connectionKey(), captured.getConnectionKey());
	}

	@DisplayName("Handshake preparation applies the decision's attributes together with a queued instruction")
	@Test
	void handshakeAppliesDecisionAttributesWithQueuedInstruction() {
		ConnectionIdentity identity = identity("PlayerThree");
		HandshakeDecision handshake = HandshakeDecision.allow().withAttribute(HandshakeAttributeKey.bool("test:now"), true);
		ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
		when(connectionCoordinator.prepare(any())).thenReturn(CompletableFuture.completedFuture(PrepareDecision.builder()
				.status(PrepareDecision.Status.ALLOW)
				.handshake(handshake)
				.build()));
		HandshakeInstruction queued = HandshakeInstruction.create(identity, 60_000L);
		queued.setAttribute(HandshakeAttributeKey.bool("test:queued"), true);
		HandshakeStore handshakeStore = mock(HandshakeStore.class);
		when(handshakeStore.consumeInstruction(any(), any())).thenReturn(Optional.of(queued));
		List<HandshakeInstruction> applied = new ArrayList<>();

		new HandshakeDecisionProcessor(connectionCoordinator, new NoopPrepareStateStore(), handshakeStore, Messages::new)
				.process(new HandshakeDecisionProcessor.Request(identity, applied::add), message -> {
				})
				.toCompletableFuture()
				.join();

		assertEquals(1, applied.size());
		assertEquals(Optional.of(true), applied.getFirst().getAttribute(HandshakeAttributeKey.bool("test:now")));
		assertEquals(Optional.of(true), applied.getFirst().getAttribute(HandshakeAttributeKey.bool("test:queued")));
	}

	private @NotNull ConnectionIdentity identity(@NotNull String username) {
		ConnectionIdentity identity = new ConnectionIdentity(username, "127.0.0.1");
		identity.setOrigin(new ConnectionIdentity.Origin("play.example.com", 25565));
		return identity;
	}

	private static final class NoopPrepareStateStore implements PrepareStateStore {
		@Override
		public void put(@NotNull String connectionKey, @NotNull PrepareDecision decision) {
		}

		@Override
		public void put(@NotNull UUID uniqueId, String connectionKey, @NotNull PrepareDecision decision) {
		}

		@Override
		public @NotNull Optional<PrepareDecision> peek(@NotNull String connectionKey) {
			return Optional.empty();
		}

		@Override
		public @NotNull Optional<PrepareDecision> peek(@NotNull UUID uniqueId) {
			return Optional.empty();
		}

		@Override
		public boolean clear(@NotNull UUID uniqueId) {
			return false;
		}
	}
}
