package me.whereareiam.identica.engine.pipeline.scenario;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.engine.connection.ConnectionDecisionResolver;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.connection.attempt.ConnectionAdvanceAttemptEvent;
import me.whereareiam.identica.event.connection.attempt.ConnectionAttemptEvent;
import me.whereareiam.identica.event.connection.attempt.ConnectionProcessAttemptEvent;
import me.whereareiam.identica.event.connection.attempt.ConnectionResumeAttemptEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.ScenarioTransitionItem;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ScenarioPipeline {
	private final ConnectionDecisionResolver decisionResolver;
	private final ScenarioRegistry scenarioRegistry;
	private final ConnectionRuns connectionRuns;
	private final PipelineStateStore pipelineStateStore;
	private final EventManager eventManager;

	public @NotNull CompletionStage<ConnectionDecision> process(@Nullable ConnectionRequest request) {
		return connectionRuns.submit(request != null ? request.getConnectionUniqueId() : null, () -> runProcess(request));
	}

	public @NotNull CompletionStage<ConnectionDecision> resume(@NotNull ResumeRequest request) {
		return connectionRuns.submit(request.getConnectionUniqueId(), () -> runResume(request));
	}

	public @NotNull CompletionStage<ConnectionDecision> advance(@NotNull AdvanceRequest request) {
		return connectionRuns.submit(request.getConnectionUniqueId(), () -> runAdvance(request));
	}

	private @NotNull CompletionStage<ConnectionDecision> runProcess(@Nullable ConnectionRequest request) {
		if (request != null) {
			ConnectionDecision entryDecision = resolveEntryDecision(new ConnectionProcessAttemptEvent(
					request.getConnectionUniqueId(),
					request.getIdentity().getAccountUniqueId(),
					request.getUsername(),
					request.getIp()
			));
			if (entryDecision != null) return CompletableFuture.completedFuture(entryDecision);
		}

		ScenarioSelection selection = scenarioRegistry.select(request);
		if (selection.isResume()) {
			return execute(
					request,
					selection.getRunner(),
					selection.getRunner().execute(null, selection.getResumeRequest()),
					true
			);
		}

		return execute(
				request,
				selection.getRunner(),
				selection.getRunner().execute(request, null),
				true
		);
	}

	private @NotNull CompletionStage<ConnectionDecision> runResume(@NotNull ResumeRequest request) {
		ConnectionDecision entryDecision = resolveEntryDecision(new ConnectionResumeAttemptEvent(
				request.getConnectionUniqueId(),
				request.getAccountUniqueId(),
				request.getUsername(),
				request.getIp()
		));
		if (entryDecision != null) return CompletableFuture.completedFuture(entryDecision);

		AbstractScenarioPipeline runner = scenarioRegistry.selectForResume(request);
		return execute(
				request.toConnectionRequest(),
				runner,
				runner.execute(null, request),
				true
		);
	}

	private @NotNull CompletionStage<ConnectionDecision> runAdvance(@NotNull AdvanceRequest request) {
		ConnectionDecision entryDecision = resolveEntryDecision(new ConnectionAdvanceAttemptEvent(
				request.getConnectionUniqueId(),
				request.getAccountUniqueId(),
				request.getUsername(),
				request.getIp()
		));
		if (entryDecision != null) return CompletableFuture.completedFuture(entryDecision);

		AbstractScenarioPipeline runner = scenarioRegistry.selectForAdvance(request);
		ResumeRequest pendingRequest = ResumeRequest.builder()
				.connectionUniqueId(request.getConnectionUniqueId())
				.identity(request.getIdentity())
				.intendedServer(request.getIntendedServer())
				.input(request.getInput())
				.build();

		return execute(
				request.toConnectionRequest(),
				runner,
				runner.executeAdvance(pendingRequest),
				true
		);
	}

	public boolean hasPending(@NotNull UUID connectionUniqueId) {
		PipelineStateReference reference = PipelineStateReference.builder()
				.connectionUniqueId(connectionUniqueId)
				.build();

		PipelineState state = pipelineStateStore.find(reference).orElse(null);
		return state != null && scenarioRegistry.isPending(state);
	}

	private @NotNull CompletionStage<ConnectionDecision> execute(
			@Nullable ConnectionRequest fallbackRequest,
			@NotNull AbstractScenarioPipeline runner,
			@NotNull CompletionStage<PipelineResult> execution,
			boolean allowTransitionRouting
	) {
		return execution.handle((result, error) -> new ExecutionOutcome(
						resolveDecision(result, error, runner.type()),
						buildRerouteRequest(fallbackRequest, runner, result),
						resolveScenarioTransition(runner, result)
				))
				.thenCompose(outcome -> finalizeOutcome(fallbackRequest, runner, outcome, allowTransitionRouting));
	}

	private @NotNull CompletionStage<ConnectionDecision> finalizeOutcome(
			@Nullable ConnectionRequest fallbackRequest,
			@NotNull AbstractScenarioPipeline runner,
			@NotNull ExecutionOutcome outcome,
			boolean allowTransitionRouting
	) {
		ScenarioTransitionItem transition = outcome.transition();
		PipelineType switchPipeline = transition != null ? transition.getTargetPipeline() : null;
		ConnectionRequest rerouteRequest = outcome.rerouteRequest() != null
				? outcome.rerouteRequest()
				: fallbackRequest;

		if (allowTransitionRouting
				&& switchPipeline != null
				&& switchPipeline != runner.type()) {
			return routeTransition(withTransition(rerouteRequest, transition), switchPipeline);
		}

		return CompletableFuture.completedFuture(outcome.decision());
	}

	private @NotNull CompletionStage<ConnectionDecision> routeTransition(
			@Nullable ConnectionRequest request,
			@NotNull PipelineType pipelineType
	) {
		AbstractScenarioPipeline runner = scenarioRegistry.resolve(pipelineType);
		if (runner == null) return CompletableFuture.completedFuture(ConnectionDecision.deny("Connection failed"));

		return execute(
				request,
				runner,
				runner.execute(request, null),
				false
		);
	}

	private @NotNull ConnectionDecision resolveDecision(
			@Nullable PipelineResult result,
			@Nullable Throwable error,
			@NotNull PipelineType pipelineType
	) {
		return decisionResolver.resolveDecision(result, error, pipelineType);
	}

	private @Nullable ConnectionDecision resolveEntryDecision(@NotNull ConnectionAttemptEvent event) {
		eventManager.call(event);
		return event.getDecision();
	}

	private @Nullable ConnectionRequest buildRerouteRequest(
			@Nullable ConnectionRequest fallbackRequest,
			@NotNull AbstractScenarioPipeline runner,
			@Nullable PipelineResult result
	) {
		if (result == null) return fallbackRequest;

		ScenarioContext context = runner.resolveContext(result);
		if (context == null || context.getIdentity().getUsername().isBlank())
			return fallbackRequest;

		ConnectionIdentity identity = context.getIdentity();
		ConnectionIdentity mergedIdentity = new ConnectionIdentity(
				identity.getAccountUniqueId(),
				identity.getObservedUniqueId(),
				identity.getUsername(),
				identity.getIp()
		);
		mergedIdentity.setOrigin(identity.getOrigin());

		return ConnectionRequest.builder()
				.connectionUniqueId(context.getConnectionUniqueId() != null
						? context.getConnectionUniqueId()
						: fallbackRequest != null ? fallbackRequest.getConnectionUniqueId() : null)
				.identity(mergedIdentity)
				.intendedServer(context.getIntendedServer() != null
						? context.getIntendedServer()
						: fallbackRequest != null ? fallbackRequest.getIntendedServer() : null)
				.provider(context.getProvider() != null
						? context.getProvider()
						: fallbackRequest != null ? fallbackRequest.getProvider() : null)
				.transition(context.getTransition() != null
						? context.getTransition()
						: fallbackRequest != null ? fallbackRequest.getTransition() : null)
				.build();
	}

	private @Nullable ScenarioTransitionItem resolveScenarioTransition(
			@NotNull AbstractScenarioPipeline runner,
			@Nullable PipelineResult result
	) {
		if (result == null) return null;

		ScenarioContext context = runner.resolveContext(result);
		return context != null ? context.getTransition() : null;
	}

	private @Nullable ConnectionRequest withTransition(
			@Nullable ConnectionRequest request,
			@Nullable ScenarioTransitionItem transition
	) {
		if (request == null || transition == null)
			return request;

		return ConnectionRequest.builder()
				.connectionUniqueId(request.getConnectionUniqueId())
				.identity(request.getIdentity())
				.intendedServer(request.getIntendedServer())
				.provider(request.getProvider())
				.transition(transition)
				.build();
	}

	private record ExecutionOutcome(
			@NotNull ConnectionDecision decision,
			@Nullable ConnectionRequest rerouteRequest,
			@Nullable ScenarioTransitionItem transition
	) {
	}
}
