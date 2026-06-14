package me.whereareiam.identica.common.adapter;

import me.whereareiam.identica.connection.ConnectionCoordinator;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Profile Rewrite Processor")
class ProfileRewriteProcessorTest {
	@DisplayName("Stores denied prepare decisions with both the observed UUID and connection key")
	@Test
	void deniedPrepareStoresStateAndCallsDenyTarget() {
		UUID observedUniqueId = UUID.randomUUID();
		TestConnectionCoordinator connectionCoordinator = new TestConnectionCoordinator(PrepareDecision.deny("denied"));
		TestPrepareStateStore prepareStateStore = new TestPrepareStateStore();
		ProfileRewriteProcessor processor = new ProfileRewriteProcessor(connectionCoordinator, prepareStateStore);
		AtomicBoolean denied = new AtomicBoolean();

		ConnectionIdentity identity = new ConnectionIdentity("PlayerOne", "127.0.0.1");
		processor.process(
						new ProfileRewriteProcessor.Request(identity, observedUniqueId, identity.getUsername()),
						new ProfileRewriteProcessor.Target() {
							@Override
							public void apply(@NotNull ProfileRewriteProcessor.Rewrite rewrite) {
							}

							@Override
							public void deny(@NotNull PrepareDecision prepared) {
								denied.set(true);
							}
						})
				.toCompletableFuture()
				.join();

		assertTrue(denied.get());
		assertEquals(observedUniqueId, prepareStateStore.uniqueId);
		assertEquals(identity.connectionKey(), prepareStateStore.connectionKey);
	}

	private static final class TestConnectionCoordinator implements ConnectionCoordinator {
		private final PrepareDecision decision;

		private TestConnectionCoordinator(PrepareDecision decision) {
			this.decision = decision;
		}

		@Override
		public @NotNull CompletableFuture<PrepareDecision> prepare(me.whereareiam.identica.model.pipeline.prepare.PrepareRequest request) {
			return CompletableFuture.completedFuture(decision);
		}

		@Override
		public @NotNull CompletableFuture<ConnectionDecision> process(ConnectionRequest request) {
			throw new UnsupportedOperationException();
		}

		@Override
		public @NotNull CompletableFuture<ConnectionDecision> resume(@NotNull ResumeRequest request) {
			throw new UnsupportedOperationException();
		}

		@Override
		public @NotNull CompletableFuture<ConnectionDecision> advance(@NotNull AdvanceRequest request) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean hasPending(@NotNull UUID connectionUniqueId) {
			return false;
		}

		@Override
		public void complete(@NotNull Identity identity) {
		}
	}

	private static final class TestPrepareStateStore implements PrepareStateStore {
		private UUID uniqueId;
		private String connectionKey;

		@Override
		public void put(@NotNull String connectionKey, @NotNull PrepareDecision decision) {
		}

		@Override
		public void put(@NotNull UUID uniqueId, String connectionKey, @NotNull PrepareDecision decision) {
			this.uniqueId = uniqueId;
			this.connectionKey = connectionKey;
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
