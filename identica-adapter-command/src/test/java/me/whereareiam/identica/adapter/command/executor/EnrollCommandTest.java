package me.whereareiam.identica.adapter.command.executor;

import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.provider.selection.ProviderSelector;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("Enroll Command")
class EnrollCommandTest {
	@BeforeAll
	static void initializeSerializer() {
		SerializerEngine engine = mock(SerializerEngine.class);
		when(engine.serialize(any(SerializerContent.class)))
				.thenAnswer(call -> Component.text(call.getArgument(0, SerializerContent.class).getMessage()));
		Serializer.initialize(() -> engine);
	}

	static Stream<ConnectionDecision> denials() {
		return Stream.of(
				ConnectionDecision.deny(message("")),
				ConnectionDecision.deny(null),
				ConnectionDecision.deny(message("Registration failed")),
				ConnectionDecision.requireReconnect(message("")),
				ConnectionDecision.requireReconnect(message("Join again"))
		);
	}

	@DisplayName("Disconnects on a denial with or without a reason")
	@ParameterizedTest(name = "{0}")
	@MethodSource("denials")
	void disconnectsOnDenial(ConnectionDecision decision) {
		ConnectionCoordinator coordinator = mock(ConnectionCoordinator.class);
		when(coordinator.resume(any())).thenReturn(CompletableFuture.completedFuture(decision));
		Identity identity = mock(Identity.class);
		when(identity.getUniqueId()).thenReturn(UUID.randomUUID());

		new EnrollCommand(coordinator, Engine::new, EnrollCommandTest::messages, mock(ProviderSelector.class), messageService()).enroll(identity, "credential");

		verify(identity).disconnect(decision.getMessage() == null ? Component.empty() : Component.text(decision.getMessage().getText()));
	}

	@DisplayName("Adds the player's choice to the selection of the pending registration and runs the chosen provider")
	@Test
	void addsThePlayersChoiceToTheSelection() {
		ConnectionCoordinator coordinator = mock(ConnectionCoordinator.class);
		when(coordinator.resume(any())).thenReturn(CompletableFuture.completedFuture(ConnectionDecision.allow()));
		ProviderSelector selector = mock(ProviderSelector.class);
		ProviderSelection chosen = ProviderSelection.of(ProviderClaim.of("alpha", ProviderClaimOrigin.PLAYER_CHOICE), List.of(), List.of());
		when(selector.claim(any(), any())).thenReturn(chosen);
		Identity identity = mock(Identity.class);
		when(identity.getUniqueId()).thenReturn(UUID.randomUUID());

		new EnrollCommand(coordinator, EnrollCommandTest::engine, EnrollCommandTest::messages, selector, messageService()).enroll(identity, "alpha");

		ArgumentCaptor<ResumeRequest> request = ArgumentCaptor.forClass(ResumeRequest.class);
		verify(coordinator).resume(request.capture());
		RegistrationContext context = RegistrationContext.builder()
				.identity(new ConnectionIdentity(UUID.randomUUID(), "Alice", "127.0.0.1"))
				.build();
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.REGISTRATION);
		state.setScenario(context);
		state.put(JourneyStateItem.KEY, new JourneyStateItem("enrollment", 0));
		request.getValue().getInput().applyTo(state);

		verify(selector).claim(any(), eq(List.of(ProviderClaim.of("alpha", ProviderClaimOrigin.PLAYER_CHOICE))));
		assertEquals(chosen, context.getSelection());
		assertEquals("alpha", context.getProvider().getProviderId());
		assertEquals("provider", state.get(JourneyStateItem.KEY).orElseThrow().getStageId());
	}

	private static MessageService messageService() {
		MessageService messageService = mock(MessageService.class);
		when(messageService.disconnectReason(any(), any()))
				.thenAnswer(call -> Component.text(call.getArgument(1, PlayerMessage.class).getText()));

		return messageService;
	}

	private static Engine engine() {
		Engine.Scenarios scenarios = new Engine.Scenarios();
		scenarios.getRegistration().setPipelineTtl(Duration.ofMinutes(1));
		Engine engine = new Engine();
		engine.setScenarios(scenarios);
		return engine;
	}

	private static Messages messages() {
		Messages messages = new Messages();
		Messages.Commands commands = new Messages.Commands();
		commands.setEnroll(new Messages.Commands.Enroll());
		messages.setCommands(commands);
		return messages;
	}

	private static PlayerMessage message(String text) {
		return PlayerMessage.builder().text(text).build();
	}
}
