package me.whereareiam.identica.engine.connection;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.engine.pipeline.scenario.AbstractScenarioPipeline;
import me.whereareiam.identica.engine.pipeline.scenario.ScenarioRegistry;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.connection.ConnectionDecisionEvent;
import me.whereareiam.identica.event.pipeline.attempt.PipelineAttemptFinishedEvent;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.message.ScenarioMessages;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.routing.RoutingSignal;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.routing.RoutingCoordinator;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ConnectionDecisionResolver {
	private final ScenarioRegistry scenarioRegistry;
	private final EventManager eventManager;
	private final RoutingCoordinator routingCoordinator;
	private final Provider<Messages> messagesProvider;

	public @NotNull ConnectionDecision resolveDecision(
			@Nullable PipelineResult result,
			@Nullable Throwable error,
			@NotNull PipelineType pipelineType
	) {
		AbstractScenarioPipeline runner = scenarioRegistry.resolve(pipelineType);
		if (error != null) {
			Logger.severe("Connection journey failed %s", error);
			return failureDecision(runner, pipelineType);
		}

		if (result == null)
			return failureDecision(runner, pipelineType);

		ScenarioContext scenarioContext = resolveScenarioContext(result, runner);
		if (scenarioContext != null) {
			eventManager.call(new PipelineAttemptFinishedEvent(scenarioContext, pipelineType, result));
			routingCoordinator.accept(RoutingSignal.pipelineFinished(scenarioContext, pipelineType, result));
		}

		ConnectionDecision decision = runner != null
				? runner.mapDecision(result)
				: ConnectionDecision.deny(ScenarioMessages.of(pipelineType).getFailed().create(messagesProvider.get()));
		if (!(scenarioContext instanceof AuthContext authContext))
			return decision;

		ConnectionDecisionEvent decisionEvent = new ConnectionDecisionEvent(authContext, decision);
		eventManager.call(decisionEvent);
		ConnectionDecision finalDecision = decisionEvent.getDecision();
		return finalDecision != null ? finalDecision : decision;
	}

	private @Nullable ScenarioContext resolveScenarioContext(
			@NotNull PipelineResult result,
			@Nullable AbstractScenarioPipeline runner
	) {
		if (runner == null) return null;

		return runner.resolveContext(result);
	}

	private @NotNull ConnectionDecision failureDecision(@Nullable AbstractScenarioPipeline runner, @NotNull PipelineType pipelineType) {
		if (runner == null)
			return ConnectionDecision.deny(ScenarioMessages.of(pipelineType).getFailed().create(messagesProvider.get()));

		return runner.failureDecision();
	}
}
