package me.whereareiam.identica.engine.pipeline.scenario.type.authentication;

import me.whereareiam.identica.common.config.defaults.EngineDefaults;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.engine.pipeline.PipelineExecutor;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.journey.JourneyOverrideItem;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.pipeline.PipelineRegistry;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.util.EventUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Authentication Pipeline")
class AuthenticationPipelineTest {
	@DisplayName("Fails to build a context when the request has no account UUID")
	@Test
	void buildContextRequiresPreparedAccountUniqueId() {
		TestAuthenticationPipeline pipeline = new TestAuthenticationPipeline();

		ScenarioContext context = pipeline.buildFor(ConnectionRequest.builder()
				.identity(new me.whereareiam.identica.identity.actor.ConnectionIdentity("PlayerOne", "127.0.0.1"))
				.build());

		assertNull(context);
	}

	@DisplayName("An advance applies the request's input to the state the run uses")
	@Test
	void advanceAppliesTheInputToTheStateOfTheRun() {
		EventUtil.initialize(mock(EventManager.class));
		UUID connectionId = UUID.randomUUID();
		PipelineState stored = PipelineState.initial();
		stored.setPipelineType(PipelineType.AUTHENTICATION);
		stored.setScenario(AuthContext.builder()
				.connectionUniqueId(connectionId)
				.accountUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(connectionId, "PlayerOne", "127.0.0.1"))
				.build());
		stored.putItem(new JourneyStateItem(null, null, 0), 60_000L);

		PipelineStateStore store = mock(PipelineStateStore.class);
		when(store.find(any(PipelineStateReference.class))).thenReturn(Optional.of(stored));
		PipelineExecutor executor = mock(PipelineExecutor.class);
		ArgumentCaptor<PipelineState> run = ArgumentCaptor.forClass(PipelineState.class);
		when(executor.execute(any(), run.capture(), any())).thenReturn(CompletableFuture.completedFuture(null));
		TestAuthenticationPipeline pipeline = new TestAuthenticationPipeline(store, executor);

		pipeline.executeAdvance(ResumeRequest.builder()
				.connectionUniqueId(connectionId)
				.input(state -> state.putItem(new JourneyOverrideItem(null, "chosen", 0, false, null, List.of()), 60_000L))
				.build()).toCompletableFuture().join();

		assertSame(stored, run.getValue(), "the run works on the state the pipeline loaded");
		assertEquals("chosen", run.getValue().item(JourneyOverrideItem.class).orElseThrow().getStageId());
	}

	private static final class TestAuthenticationPipeline extends AuthenticationPipeline {
		private TestAuthenticationPipeline() {
			this(mock(PipelineStateStore.class), mock(PipelineExecutor.class));
		}

		private TestAuthenticationPipeline(PipelineStateStore store, PipelineExecutor executor) {
			super(
					mock(PipelineRegistry.class),
					TestAuthenticationPipeline::messages,
					() -> new EngineDefaults().supply(new Engine()),
					store,
					executor,
					mock(ProviderLinkPersistenceService.class)
			);
		}

		private static Messages messages() {
			Messages.Scenarios.Authentication authentication = new Messages.Scenarios.Authentication();
			authentication.setNoCompletionPipeline(List.of("no completion"));
			Messages.Scenarios scenarios = new Messages.Scenarios();
			scenarios.setAuthentication(authentication);

			Messages messages = new Messages();
			messages.setScenarios(scenarios);
			return messages;
		}

		private @Nullable ScenarioContext buildFor(@NotNull ConnectionRequest request) {
			return buildContext(request);
		}
	}
}
