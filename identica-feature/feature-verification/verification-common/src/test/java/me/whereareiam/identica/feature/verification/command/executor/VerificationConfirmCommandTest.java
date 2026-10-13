package me.whereareiam.identica.feature.verification.command.executor;

import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationCodeAttempt;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.state.PipelineInput;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@code /2fa confirm} hands the typed code to the pending journey. The pipeline asks in the connection's turn whether
 * the journey waits for a code, so a code typed while another run of the connection still holds the journey's state
 * is not turned away.
 */
@DisplayName("Verification Confirm Command")
class VerificationConfirmCommandTest {
	private static final String NO_PENDING = "Nothing to confirm.";

	private final UUID accountUniqueId = UUID.randomUUID();
	private final Identity identity = mock(Identity.class);
	private final PipelineStateStore states = mock(PipelineStateStore.class);
	private final VerificationService verification = mock(VerificationService.class);
	private final ConnectionCoordinator coordinator = mock(ConnectionCoordinator.class);

	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.serialize(any(SerializerContent.class)))
				.thenAnswer(call -> Component.text(call.getArgument(0, SerializerContent.class).getMessage()));
		Serializer.initialize(() -> engine);
	}

	@BeforeEach
	void connectedPlayer() {
		when(identity.getAccountUniqueId()).thenReturn(accountUniqueId);
		when(identity.getConnectionUniqueId()).thenReturn(UUID.randomUUID());
		when(states.find(any(PipelineStateReference.class))).thenReturn(Optional.empty());
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

	@DisplayName("Disconnects when the journey denies after the code, with or without a reason")
	@ParameterizedTest(name = "{0}")
	@MethodSource("denials")
	void disconnectsOnDenialAfterTheCode(ConnectionDecision decision) {
		answers(decision);

		confirm("123456");

		verify(identity).disconnect(Component.text(decision.getMessage() == null ? "" : decision.getMessage()));
	}

	@DisplayName("Hands a code typed before its prompt to the pipeline instead of looking for the journey itself")
	@Test
	void handsTheCodeToThePipeline() {
		answers(ConnectionDecision.waiting("Invalid code."));

		confirm("123456");

		PipelineState state = PipelineState.initial();
		input().applyTo(state);
		assertEquals("123456", state.get(VerificationCodeAttempt.KEY).orElseThrow().getCode());
		verify(identity).sendMessage(Component.text("Invalid code."));
		verify(identity, never()).sendMessage(Component.text(NO_PENDING));
	}

	@DisplayName("The code is taken only by a journey whose authentication challenge waits for it")
	@Test
	void codeFitsOnlyAJourneyWithAnActiveChallenge() {
		answers(ConnectionDecision.noPending());
		confirm("123456");
		PipelineInput input = input();

		when(verification.hasActiveChallenge(accountUniqueId, "credential", "authentication")).thenReturn(true);
		assertTrue(input.accepts(pendingState(true)));
		assertFalse(input.accepts(pendingState(false)), "a journey that is not waiting must decline the code");

		when(verification.hasActiveChallenge(accountUniqueId, "credential", "authentication")).thenReturn(false);
		assertFalse(input.accepts(pendingState(true)), "a journey waiting for its password must decline the code");
	}

	@DisplayName("Shows the no-pending message when the pipeline declines the code")
	@Test
	void showsNoPendingWhenDeclined() {
		answers(ConnectionDecision.noPending());

		confirm("123456");

		verify(identity).sendMessage(Component.text(NO_PENDING));
	}

	private void answers(ConnectionDecision decision) {
		when(coordinator.advance(any())).thenReturn(CompletableFuture.completedFuture(decision));
	}

	private void confirm(String code) {
		VerificationMessages.Commands.Confirm confirm = new VerificationMessages.Commands.Confirm();
		confirm.setNoPending(NO_PENDING);
		VerificationMessages.Commands commands = new VerificationMessages.Commands();
		commands.setConfirm(confirm);
		VerificationMessages messages = new VerificationMessages();
		messages.setCommands(commands);

		new VerificationConfirmCommand(
				Messages::new,
				() -> messages,
				VerificationSettings::new,
				verification,
				states,
				coordinator,
				mock(VerificationResultRenderer.class),
				mock(SessionService.class)
		).confirm(identity, code);
	}

	private PipelineInput input() {
		ArgumentCaptor<AdvanceRequest> request = ArgumentCaptor.forClass(AdvanceRequest.class);
		verify(coordinator).advance(request.capture());
		return request.getValue().getInput();
	}

	private PipelineState pendingState(boolean waiting) {
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.AUTHENTICATION);
		state.setScenario(AuthContext.builder()
				.identity(new ConnectionIdentity(accountUniqueId, "PlayerOne", "127.0.0.1"))
				.provider(ProviderContext.of("credential", "subject", "PlayerOne", null))
				.build());
		if (waiting) state.put(JourneyStateItem.KEY, new JourneyStateItem(null, null, 0), 60_000L);
		return state;
	}
}
