package me.whereareiam.identica.feature.verification.command.executor;

import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.model.challenge.VerificationChallengeResult;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.type.status.VerificationChallengeStatus;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Verification Confirm Command")
class VerificationConfirmCommandTest {
	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.serialize(any(SerializerContent.class)))
				.thenAnswer(call -> Component.text(call.getArgument(0, SerializerContent.class).getMessage()));
		Serializer.initialize(() -> engine);
	}

	static Stream<ConnectionDecision> denials() {
		return Stream.of(
				ConnectionDecision.deny(""),
				ConnectionDecision.deny(null),
				ConnectionDecision.deny("Sign-in failed"),
				ConnectionDecision.requireReconnect(""),
				ConnectionDecision.requireReconnect("Join again")
		);
	}

	@DisplayName("Disconnects when the journey denies after a verified code, with or without a reason")
	@ParameterizedTest(name = "{0}")
	@MethodSource("denials")
	@SuppressWarnings({"unchecked", "rawtypes"})
	void disconnectsOnDenialAfterVerifiedCode(ConnectionDecision decision) {
		UUID accountUniqueId = UUID.randomUUID();
		Identity identity = mock(Identity.class);
		when(identity.getAccountUniqueId()).thenReturn(accountUniqueId);
		when(identity.getConnectionUniqueId()).thenReturn(UUID.randomUUID());

		PipelineState state = PipelineState.initial();
		state.putItem(new JourneyStateItem(null, null, 0), 60_000L);
		PipelineStateStore states = mock(PipelineStateStore.class);
		when(states.find(any(PipelineStateReference.class))).thenReturn(Optional.of(state));

		VerificationService verification = mock(VerificationService.class);
		VerificationChallengeResult verified = VerificationChallengeResult.builder()
				.status(VerificationChallengeStatus.VERIFIED)
				.build();
		when(verification.submitChallenge(any(UUID.class), any(), any(), any())).thenReturn(verified);

		ConnectionCoordinator coordinator = mock(ConnectionCoordinator.class);
		when(coordinator.advance(any())).thenReturn(CompletableFuture.completedFuture(decision));

		VerificationMessages messages = new VerificationMessages();
		messages.setCommands(new VerificationMessages.Commands());
		new VerificationConfirmCommand(
				Messages::new,
				() -> messages,
				VerificationSettings::new,
				verification,
				states,
				coordinator,
				mock(VerificationResultRenderer.class),
				mock(SessionService.class)
		).confirm(identity, "123456");

		verify(identity).disconnect(Component.text(decision.getMessage() == null ? "" : decision.getMessage()));
	}
}
