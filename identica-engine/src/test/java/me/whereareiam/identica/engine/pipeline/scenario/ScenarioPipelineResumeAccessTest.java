package me.whereareiam.identica.engine.pipeline.scenario;

import me.whereareiam.identica.engine.pipeline.PipelineExecutor;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.AuthenticationPipeline;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.pipeline.PipelineRegistry;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who may continue a saved login. The connection that saved it always continues it; a rejoin of the player only while
 * {@code allowResume} is on, or when the login asked the player to reconnect.
 */
@DisplayName("Scenario Pipeline Resume Access")
class ScenarioPipelineResumeAccessTest {
	private final UUID player = UUID.randomUUID();
	private final PipelineStateStore store = mock(PipelineStateStore.class);
	private final PipelineExecutor executor = mock(PipelineExecutor.class);

	@DisplayName("The connection that saved a login continues it, with or without allowResume")
	@ParameterizedTest(name = "allowResume {0}")
	@ValueSource(booleans = {true, false})
	void theSavingConnectionContinues(boolean allowResume) {
		when(store.consume(any(ResumeRequest.class))).thenReturn(Optional.of(saved()));
		when(executor.execute(any(), any(), any())).thenReturn(new CompletableFuture<>());

		pipeline(allowResume).execute(null, ResumeRequest.builder()
				.connectionUniqueId(player)
				.identity(identity())
				.build());

		verify(executor).execute(any(), any(), any());
	}

	@DisplayName("A rejoin continues a login while allowResume is on")
	@Test
	void aRejoinContinuesWithResume() {
		assertTrue(pipeline(true).acceptsRejoin(saved()));
	}

	@DisplayName("A rejoin starts over while allowResume is off")
	@Test
	void aRejoinStartsOverWithoutResume() {
		assertFalse(pipeline(false).acceptsRejoin(saved()));
	}

	@DisplayName("The reconnect a login asked for continues it while allowResume is off")
	@Test
	void aRequestedReconnectContinuesWithoutResume() {
		PipelineState saved = saved();
		saved.put(AbstractScenarioPipeline.RECONNECT_REQUESTED, System.currentTimeMillis());

		assertTrue(pipeline(false).acceptsRejoin(saved));
	}

	@DisplayName("A requested reconnect survives the saved state's round trip")
	@Test
	void theRequestedReconnectIsSaved() {
		PipelineState saved = saved();
		saved.put(AbstractScenarioPipeline.RECONNECT_REQUESTED, 42L);

		PipelineState restored = PipelineState.restore(saved.store());

		assertEquals(Optional.of(42L), restored.get(AbstractScenarioPipeline.RECONNECT_REQUESTED));
	}

	private PipelineState saved() {
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.AUTHENTICATION);
		state.setScenario(AuthContext.builder()
				.connectionUniqueId(player)
				.identity(identity())
				.build());
		state.put(JourneyStateItem.KEY, new JourneyStateItem(null, null, 0), 60_000L);
		return state;
	}

	private ConnectionIdentity identity() {
		ConnectionIdentity identity = new ConnectionIdentity(player, "PlayerOne", "127.0.0.1");
		identity.setConnectionUniqueId(player);
		return identity;
	}

	private AuthenticationPipeline pipeline(boolean allowResume) {
		Engine.Authentication authentication = new Engine.Authentication();
		authentication.setAllowResume(allowResume);
		authentication.setPipelineTtl(Duration.ofMinutes(1));
		Engine.Scenarios scenarios = new Engine.Scenarios();
		scenarios.setAuthentication(authentication);
		Engine engine = new Engine();
		engine.setScenarios(scenarios);

		return new AuthenticationPipeline(
				mock(PipelineRegistry.class),
				Messages::new,
				() -> engine,
				store,
				executor,
				mock(ProviderLinkPersistenceService.class)
		);
	}
}
