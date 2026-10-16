package me.whereareiam.identica.addon.dialogs.trigger;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.step.StepFinishedEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import me.whereareiam.identica.type.pipeline.journey.step.StepWaitReason;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StepWaitingTriggerTest {
	private static final UUID ALICE = UUID.randomUUID();

	private final EventManager events = mock(EventManager.class);
	private final RecordingSink sink = new RecordingSink();
	private final StepWaitingTrigger trigger = new StepWaitingTrigger(events);

	@Test
	void reportsAWaitingStepWithItsAttributesAndAnswersOnlyWhenItWaitsAgain() {
		trigger.start(sink);
		verify(events).register(trigger);

		trigger.onStepFinished(event("password-authentication-password", StepResult.waiting("{prefix}Use /login")));
		trigger.onStepFinished(event("password-authentication-password", StepResult.waiting("{prefix}Invalid password.")));

		TriggerContext first = sink.fired.getFirst();
		assertEquals(Map.of(
				"scenario", "AUTHENTICATION",
				"stage", "provider",
				"step", "password-authentication-password",
				"provider", "credential",
				"host", "play.example.net",
				"server", "lobby"
		), first.getAttributes());
		assertEquals(Map.of("message", ""), first.getPlaceholders());
		assertEquals(Map.of("message", "{prefix}Invalid password."), sink.fired.get(1).getPlaceholders());
		assertEquals(ALICE, first.getConnectionUniqueId());
		assertEquals("step-waiting", first.getTrigger());
	}

	@Test
	void settlesOnceTheWaitingStepFinishesOtherwise() {
		trigger.start(sink);
		trigger.onStepFinished(event("password-registration", StepResult.waiting("Use /pass")));

		trigger.onStepFinished(event("password-validation", StepResult.proceed(null)));
		trigger.onStepFinished(event("password-registration", StepResult.proceed(null)));
		trigger.onStepFinished(event("password-registration", StepResult.complete(null)));

		assertEquals(List.of(ALICE), sink.settled);
	}

	@Test
	void ignoresAStepWaitingForThePlayerToBeOnline() {
		trigger.start(sink);

		trigger.onStepFinished(event("password-registration", StepResult.waiting("", StepWaitReason.ONLINE)));

		assertEquals(List.of(), sink.fired);
		assertEquals(List.of(), sink.settled);
	}

	@Test
	void stopsReportingWhenStopped() {
		trigger.start(sink);
		trigger.stop();

		trigger.onStepFinished(event("password-registration", StepResult.waiting("Use /pass")));

		verify(events).unregister(trigger);
		assertEquals(List.of(), sink.fired);
	}

	private static StepFinishedEvent event(String step, StepResult result) {
		Step finished = mock(Step.class);
		when(finished.getName()).thenReturn(step);

		ConnectionIdentity identity = mock(ConnectionIdentity.class);
		when(identity.getOrigin()).thenReturn(new ConnectionIdentity.Origin("play.example.net", 25565));
		ProviderContext provider = mock(ProviderContext.class);
		when(provider.getProviderId()).thenReturn("credential");
		ScenarioContext context = mock(ScenarioContext.class);
		when(context.getConnectionUniqueId()).thenReturn(ALICE);
		when(context.getIdentity()).thenReturn(identity);
		when(context.getProvider()).thenReturn(provider);
		when(context.getIntendedServer()).thenReturn("lobby");

		return new StepFinishedEvent(null, finished, context, PipelineType.AUTHENTICATION, result, StageType.PROVIDER);
	}

	private static final class RecordingSink implements TriggerSink {
		private final List<TriggerContext> fired = new ArrayList<>();
		private final List<UUID> settled = new ArrayList<>();

		@Override
		public void fire(@NotNull TriggerContext context) {
			fired.add(context);
		}

		@Override
		public void settle(@NotNull UUID connectionUniqueId, @NotNull String trigger) {
			settled.add(connectionUniqueId);
		}
	}
}
