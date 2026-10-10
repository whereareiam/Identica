package me.whereareiam.identica.engine.pipeline.scenario;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.AuthenticationPipeline;
import me.whereareiam.identica.engine.pipeline.scenario.type.migration.MigrationPipeline;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.RegistrationPipeline;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ScenarioRegistry {
	private final PipelineStateStore pipelineStateStore;
	private final List<AbstractScenarioPipeline> runners;
	private final Map<PipelineType, AbstractScenarioPipeline> runnersByType = new EnumMap<>(PipelineType.class);

	@Inject
	public ScenarioRegistry(
			@NotNull PipelineStateStore pipelineStateStore,
			@NotNull AuthenticationPipeline authenticationPipeline,
			@NotNull RegistrationPipeline registrationPipeline,
			@NotNull MigrationPipeline migrationPipeline
	) {
		this.pipelineStateStore = pipelineStateStore;
		this.runners = List.of(registrationPipeline, authenticationPipeline, migrationPipeline);
		runnersByType.put(authenticationPipeline.type(), authenticationPipeline);
		runnersByType.put(registrationPipeline.type(), registrationPipeline);
		runnersByType.put(migrationPipeline.type(), migrationPipeline);
	}

	public @NotNull ScenarioSelection select(@Nullable ConnectionRequest request) {
		ResumeRequest resumeRequest = buildResumeRequest(request);
		if (resumeRequest != null) {
			AbstractScenarioPipeline resumeRunner = resolveResumeRunner(resumeRequest);
			if (resumeRunner != null && belongsToAnotherProvider(resumeRequest, request)) {
				// The pending pipeline was started for another provider, so it is not this connection's to finish.
				pipelineStateStore.clear(PipelineStateReference.from(resumeRequest));
				resumeRunner = null;
			}

			if (resumeRunner != null) {
				Logger.debug(
						"Scenario select chose resume pipeline=%s connection=%s identity=%s key=%s",
						resumeRunner.type(),
						resumeRequest.getConnectionUniqueId(),
						resumeRequest.getAccountUniqueId(),
						resumeRequest.getIdentity() != null ? resumeRequest.getIdentity().connectionKey() : null
				);
				return ScenarioSelection.resume(resumeRunner, resumeRequest);
			}
		}

		AbstractScenarioPipeline runner = selectNewScenario(request);
		Logger.debug(
				"Scenario select chose new pipeline pipeline=%s connection=%s identity=%s key=%s",
				runner.type(),
				request != null ? request.getConnectionUniqueId() : null,
                request != null ? request.getIdentity().getAccountUniqueId() : null,
                request != null ? request.getIdentity().connectionKey() : null
		);
		return ScenarioSelection.newScenario(runner);
	}

	public @NotNull AbstractScenarioPipeline selectNewScenario(@Nullable ConnectionRequest request) {
		for (AbstractScenarioPipeline runner : runners) {
			if (runner.matchesNewScenario(request))
				return runner;
		}

		AbstractScenarioPipeline fallback = runnersByType.get(PipelineType.AUTHENTICATION);
		return fallback != null ? fallback : runners.getFirst();
	}

	public @NotNull AbstractScenarioPipeline selectForResume(@NotNull ResumeRequest request) {
		AbstractScenarioPipeline resumeRunner = resolveResumeRunner(request);
		if (resumeRunner != null)
			return resumeRunner;

		ConnectionRequest fallbackRequest = toConnectionRequest(request);
		return selectNewScenario(fallbackRequest);
	}

	public @NotNull AbstractScenarioPipeline selectForAdvance(@NotNull AdvanceRequest request) {
		AbstractScenarioPipeline advanceRunner = resolveAdvanceRunner(request);
		if (advanceRunner != null)
			return advanceRunner;

		ConnectionRequest fallbackRequest = toConnectionRequest(request);
		return selectNewScenario(fallbackRequest);
	}

	public @Nullable AbstractScenarioPipeline resolve(@NotNull PipelineType type) {
		return runnersByType.get(type);
	}

	public boolean isPending(@NotNull PipelineState state) {
		PipelineType pipelineType = state.getPipelineType();
		if (pipelineType != null) {
			AbstractScenarioPipeline runner = runnersByType.get(pipelineType);
			return runner != null && runner.isPending(state);
		}

		for (AbstractScenarioPipeline runner : runners) {
			if (runner.isPending(state)) return true;
		}

		return false;
	}

	private @Nullable AbstractScenarioPipeline resolveResumeRunner(@NotNull ResumeRequest request) {
		PipelineStateReference reference = PipelineStateReference.from(request);
		PipelineState stored = pipelineStateStore.find(reference).orElse(null);
		if (stored == null) {
			Logger.debug(
					"Scenario resume lookup missed connection=%s identity=%s key=%s",
					reference.getConnectionUniqueId(),
					reference.getAccountUniqueId(),
					reference.getConnectionKey()
			);
			return null;
		}

		PipelineType storedType = stored.getPipelineType();
		if (storedType == null) {
			Logger.debug(
					"Scenario resume lookup found state without pipeline type connection=%s identity=%s key=%s",
					reference.getConnectionUniqueId(),
					reference.getAccountUniqueId(),
					reference.getConnectionKey()
			);
			return null;
		}

		AbstractScenarioPipeline runner = runnersByType.get(storedType);
		if (runner == null) {
			Logger.debug(
					"Scenario resume lookup found unmapped pipeline type=%s connection=%s identity=%s key=%s",
					storedType,
					reference.getConnectionUniqueId(),
					reference.getAccountUniqueId(),
					reference.getConnectionKey()
			);
			return null;
		}

		boolean pending = runner.isPending(stored);
		Logger.debug(
				"Scenario resume lookup found pipeline=%s pending=%s connection=%s identity=%s key=%s",
				storedType,
				pending,
				reference.getConnectionUniqueId(),
				reference.getAccountUniqueId(),
				reference.getConnectionKey()
		);
		return pending ? runner : null;
	}

	private boolean belongsToAnotherProvider(@NotNull ResumeRequest resumeRequest, @NotNull ConnectionRequest request) {
		ProviderContext connecting = request.getProvider();
		if (connecting == null || isBlank(connecting.getProviderId())) return false;

		PipelineState stored = pipelineStateStore.find(PipelineStateReference.from(resumeRequest)).orElse(null);
		ScenarioContext scenario = stored != null ? stored.getScenario() : null;
		ProviderContext pending = scenario != null ? scenario.getProvider() : null;
		if (pending == null || isBlank(pending.getProviderId())) return false;
		if (pending.getProviderId().equalsIgnoreCase(connecting.getProviderId())) return false;

		Logger.debug(
				"Scenario select discarded pending pipeline of another provider pipeline=%s pending=%s connecting=%s connection=%s key=%s",
				stored.getPipelineType(),
				pending.getProviderId(),
				connecting.getProviderId(),
				resumeRequest.getConnectionUniqueId(),
				resumeRequest.getIdentity() != null ? resumeRequest.getIdentity().connectionKey() : null
		);
		return true;
	}

	private static boolean isBlank(@Nullable String value) {
		return value == null || value.isBlank();
	}

	private @Nullable AbstractScenarioPipeline resolveAdvanceRunner(@NotNull AdvanceRequest request) {
		PipelineState stored = pipelineStateStore.find(PipelineStateReference.from(request)).orElse(null);
		if (stored == null) return null;

		PipelineType storedType = stored.getPipelineType();
		if (storedType != null) {
			AbstractScenarioPipeline runner = runnersByType.get(storedType);
			if (runner != null && runner.isPending(stored))
				return runner;
		}

		for (AbstractScenarioPipeline runner : runners) {
			if (runner.isPending(stored))
				return runner;
		}

		return null;
	}

	private @Nullable ResumeRequest buildResumeRequest(@Nullable ConnectionRequest request) {
		if (request == null) return null;
		return ResumeRequest.builder()
				.connectionUniqueId(request.getConnectionUniqueId())
				.identity(request.getIdentity())
				.intendedServer(request.getIntendedServer())
				.build();
	}

	private @Nullable ConnectionRequest toConnectionRequest(@NotNull ResumeRequest request) {
		if (!request.hasIdentity()) return null;
		return ConnectionRequest.builder()
				.connectionUniqueId(request.getConnectionUniqueId())
				.identity(request.getIdentity())
				.intendedServer(request.getIntendedServer())
				.build();
	}

	private @Nullable ConnectionRequest toConnectionRequest(@NotNull AdvanceRequest request) {
		if (!request.hasIdentity()) return null;
		return ConnectionRequest.builder()
				.connectionUniqueId(request.getConnectionUniqueId())
				.identity(request.getIdentity())
				.intendedServer(request.getIntendedServer())
				.build();
	}
}
