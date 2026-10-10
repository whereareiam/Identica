package me.whereareiam.identica.engine.pipeline.scenario;

import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.AuthenticationPipeline;
import me.whereareiam.identica.engine.pipeline.scenario.type.migration.MigrationPipeline;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.RegistrationPipeline;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Scenario Registry")
class ScenarioRegistryTest {
	@Mock
	private PipelineStateStore pipelineStateStore;
	@Mock
	private AuthenticationPipeline authenticationPipeline;
	@Mock
	private RegistrationPipeline registrationPipeline;
	@Mock
	private MigrationPipeline migrationPipeline;

	private ScenarioRegistry registry;

	@BeforeEach
	void setUp() {
		when(authenticationPipeline.type()).thenReturn(PipelineType.AUTHENTICATION);
		when(registrationPipeline.type()).thenReturn(PipelineType.REGISTRATION);
		when(migrationPipeline.type()).thenReturn(PipelineType.MIGRATION);
		when(registrationPipeline.isPending(any())).thenReturn(true);
		when(registrationPipeline.matchesNewScenario(any())).thenReturn(true);

		registry = new ScenarioRegistry(pipelineStateStore, authenticationPipeline, registrationPipeline, migrationPipeline);
	}

	@DisplayName("Resumes a pending pipeline of the provider the connection resolves to")
	@Test
	void resumesAPendingPipelineOfTheSameProvider() {
		pending("credential");

		ScenarioSelection selection = registry.select(request("credential"));

		assertTrue(selection.isResume());
		assertSame(registrationPipeline, selection.getRunner());
		verify(pipelineStateStore, never()).clear(any(PipelineStateReference.class));
	}

	@DisplayName("Discards a pending pipeline of another provider and starts a new one")
	@Test
	void discardsAPendingPipelineOfAnotherProvider() {
		pending("credential");

		ScenarioSelection selection = registry.select(request("premium"));

		assertFalse(selection.isResume());
		verify(pipelineStateStore).clear(any(PipelineStateReference.class));
	}

	@DisplayName("Resumes a pending pipeline when the connection has not resolved a provider")
	@Test
	void resumesWhenTheConnectionHasNoProvider() {
		pending("credential");

		ScenarioSelection selection = registry.select(request(null));

		assertTrue(selection.isResume());
		verify(pipelineStateStore, never()).clear(any(PipelineStateReference.class));
	}

	@DisplayName("Resumes a pending pipeline that has not chosen a provider")
	@Test
	void resumesWhenThePendingPipelineHasNoProvider() {
		pending(null);

		ScenarioSelection selection = registry.select(request("premium"));

		assertTrue(selection.isResume());
		verify(pipelineStateStore, never()).clear(any(PipelineStateReference.class));
	}

	private void pending(String providerId) {
		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.REGISTRATION);
		state.setScenario(RegistrationContext.builder()
				.identity(new ConnectionIdentity("Alice", "127.0.0.1"))
				.provider(provider(providerId))
				.build());
		when(pipelineStateStore.find(any(PipelineStateReference.class))).thenReturn(Optional.of(state));
	}

	private ConnectionRequest request(String providerId) {
		return ConnectionRequest.builder()
				.identity(new ConnectionIdentity("Alice", "127.0.0.1"))
				.provider(provider(providerId))
				.build();
	}

	private ProviderContext provider(String providerId) {
		return ProviderContext.of(providerId, providerId == null ? null : providerId + "-subject", "Alice", ProviderOrigin.AUTO);
	}
}
