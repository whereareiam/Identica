package me.whereareiam.identica.platform.common.stage.profile;

import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.platform.common.stage.login.PreparedRefusal;
import me.whereareiam.identica.platform.common.stage.TextSerializerEngine;
import me.whereareiam.identica.platform.common.stage.PrepareDenialMessages;
import me.whereareiam.identica.platform.common.stage.InMemoryPrepareStateStore;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Profile Rewrite Processor")
class ProfileRewriteProcessorTest {
	private final UUID observedUniqueId = UUID.randomUUID();
	private final ConnectionIdentity identity = new ConnectionIdentity("PlayerOne", "127.0.0.1");
	private final ConnectionCoordinator connectionCoordinator = mock(ConnectionCoordinator.class);
	private final InMemoryPrepareStateStore store = new InMemoryPrepareStateStore();
	private final AtomicReference<ProfileOutcome.Rewritten> applied = new AtomicReference<>();

	@BeforeAll
	static void initializeSerializer() {
		Serializer.initialize(TextSerializerEngine::new);
	}

	@DisplayName("Refuses a denied profile and stores the denial under the observed UUID and connection key")
	@Test
	void deniedProfileIsRefusedAndStored() {
		PrepareDecision denied = PrepareDecision.deny("denied");

		ProfileOutcome outcome = process(CompletableFuture.completedFuture(denied));

		assertEquals(new ProfileOutcome.Refused(Component.text("denied")), outcome);
		assertNull(applied.get());
		assertEquals(Optional.of(denied), store.peek(observedUniqueId));
		assertEquals(Optional.of(denied), store.peek(identity.connectionKey()));
	}

	@DisplayName("Refuses a denial without text with the prepare denial message")
	@Test
	void deniedProfileWithoutTextIsRefusedWithPrepareDenialMessage() {
		ProfileOutcome outcome = process(CompletableFuture.completedFuture(PrepareDecision.deny(" ")));

		assertEquals(new ProfileOutcome.Refused(Component.text("refused\ncontact us")), outcome);
	}

	@DisplayName("Rewrites an allowed profile through the target")
	@Test
	void allowedProfileIsRewritten() {
		ProfileOutcome outcome = process(CompletableFuture.completedFuture(PrepareDecision.allow()));

		ProfileOutcome.Rewritten rewrite = new ProfileOutcome.Rewritten(observedUniqueId, "PlayerOne");
		assertEquals(rewrite, outcome);
		assertEquals(rewrite, applied.get());
	}

	@DisplayName("Reports a failed run and forgets a refusal stored by an earlier attempt")
	@Test
	void failedRunForgetsEarlierDecision() {
		store.put(observedUniqueId, identity.connectionKey(), PrepareDecision.deny("earlier"));
		IllegalStateException failure = new IllegalStateException("prepare failed");

		ProfileOutcome outcome = process(CompletableFuture.failedFuture(failure));

		assertInstanceOf(ProfileOutcome.Failed.class, outcome);
		assertNull(applied.get());
		assertTrue(store.byUniqueId.isEmpty());
		assertTrue(store.byConnectionKey.isEmpty());
	}

	@DisplayName("Keeps this attempt's handshake decision after a failed run, so the login keeps its provider context")
	@Test
	void failedRunKeepsHandshakeDecision() {
		store.put(observedUniqueId, identity.connectionKey(), PrepareDecision.deny("earlier"));
		PrepareDecision handshake = PrepareDecision.allow();
		store.put(identity.connectionKey(), handshake);

		ProfileOutcome outcome = process(CompletableFuture.failedFuture(new IllegalStateException("prepare failed")));

		assertInstanceOf(ProfileOutcome.Failed.class, outcome);
		assertEquals(Optional.empty(), store.peek(observedUniqueId));
		assertEquals(Optional.of(handshake), store.peek(identity.connectionKey()));
	}

	@DisplayName("Keeps an allowed decision after a failed run")
	@Test
	void failedRunKeepsAllowedDecision() {
		PrepareDecision allowed = PrepareDecision.allow();
		store.put(observedUniqueId, identity.connectionKey(), allowed);

		process(CompletableFuture.failedFuture(new IllegalStateException("prepare failed")));

		assertEquals(Optional.of(allowed), store.peek(observedUniqueId));
		assertEquals(Optional.of(allowed), store.peek(identity.connectionKey()));
	}

	private ProfileOutcome process(CompletableFuture<PrepareDecision> decision) {
		when(connectionCoordinator.prepare(any())).thenReturn(decision);
		ProfileRewriteProcessor processor = new ProfileRewriteProcessor(
				connectionCoordinator,
				store,
				new PreparedRefusal(store, PrepareDenialMessages::messages)
		);

		return processor.process(new ProfileRewriteProcessor.Request(identity, observedUniqueId, identity.getUsername()), applied::set)
				.toCompletableFuture()
				.join();
	}
}
