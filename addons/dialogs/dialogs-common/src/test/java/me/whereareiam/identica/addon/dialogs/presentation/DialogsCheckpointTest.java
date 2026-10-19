package me.whereareiam.identica.addon.dialogs.presentation;

import me.whereareiam.identica.addon.dialogs.model.LoadingScreenRule;
import me.whereareiam.identica.addon.dialogs.platform.DialogPlatform;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.checkpoint.CheckpointRequest;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.type.checkpoint.CheckpointPlacement;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogsCheckpointTest {
	private static final UUID ALICE = UUID.randomUUID();
	private static final LoadingScreenRule ANYONE = LoadingScreenRule.builder().enabled(true).build();
	private static final LoadingScreenRule EVENT_HOST = LoadingScreenRule.builder().enabled(true).when("host", "event.example.net").build();
	private static final LoadingScreenRule OFF = LoadingScreenRule.builder().enabled(false).build();

	private final DialogPlatform platform = mock(DialogPlatform.class);

	@Test
	void asksOnTheLoadingScreenWhereTheProxyCanAndOnTheStepsServerOtherwise() {
		assertEquals(List.of(CheckpointPlacement.LOADING_SCREEN, CheckpointPlacement.STEP_SERVER), checkpoint(ANYONE).placements());
	}

	@Test
	void takesTheConnectionsOfClientsThatShowDialogsWhenTheRuleAsksForThem() {
		when(platform.supports(ALICE)).thenReturn(true);

		assertTrue(checkpoint(ANYONE).accepts(request("play.example.net")));
		assertTrue(checkpoint(EVENT_HOST).accepts(request("Event.Example.Net")));
		assertFalse(checkpoint(EVENT_HOST).accepts(request("play.example.net")));
		assertFalse(checkpoint(OFF).accepts(request("play.example.net")));
	}

	@Test
	void leavesAClientThatShowsNoDialogsToChat() {
		when(platform.supports(ALICE)).thenReturn(false);

		assertFalse(checkpoint(ANYONE).accepts(request("play.example.net")));
	}

	private DialogsCheckpoint checkpoint(LoadingScreenRule rule) {
		return new DialogsCheckpoint(platform, mock(DialogPresenter.class), () -> rule);
	}

	private static CheckpointRequest request(String host) {
		ConnectionIdentity identity = new ConnectionIdentity("Alice", "127.0.0.1");
		identity.setOrigin(new ConnectionIdentity.Origin(host, 25565));
		ScenarioContext context = mock(ScenarioContext.class);
		when(context.getIdentity()).thenReturn(identity);
		return new CheckpointRequest(ALICE, context, PipelineType.REGISTRATION, StageType.PROVIDER, mock(Step.class), StepResult.waiting(null));
	}
}
