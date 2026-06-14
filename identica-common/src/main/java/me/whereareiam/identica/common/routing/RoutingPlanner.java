package me.whereareiam.identica.common.routing;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Routing;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.routing.RoutingEndpoint;
import me.whereareiam.identica.model.routing.RoutingIntent;
import me.whereareiam.identica.model.routing.RoutingPlan;
import me.whereareiam.identica.model.routing.RoutingSignal;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class RoutingPlanner {
	private final Provider<Routing> routingProvider;

	public @NotNull RoutingPlan plan(@NotNull RoutingSignal signal) {
		UUID connectionId = signal.connectionUniqueId();
		if (connectionId == null) {
			Logger.debug("Routing signal ignored because connection id is missing type=%s pipeline=%s",
					signal.getType(), signal.getPipelineType());
			return RoutingPlan.ignore();
		}

		return switch (signal.getType()) {
			case STEP_FINISHED -> planStep(signal, connectionId);
			case PIPELINE_FINISHED -> planPipeline(signal, connectionId);
		};
	}

	private @NotNull RoutingPlan planStep(@NotNull RoutingSignal signal, @NotNull UUID connectionId) {
		StepResult result = signal.getStepResult();
		if (result == null) {
			Logger.debug("Step routing ignored because step result is missing connection=%s pipeline=%s stage=%s step=%s",
					connectionId, signal.getPipelineType(), stageId(signal), stepName(signal));
			return RoutingPlan.ignore();
		}
		if (result.getStatus() == StepResult.StepStatus.COMPLETE) {
			Logger.debug("Step routing ignored for completed step connection=%s pipeline=%s stage=%s step=%s",
					connectionId, signal.getPipelineType(), stageId(signal), stepName(signal));
			return RoutingPlan.ignore();
		}
		if (result.getStatus() != StepResult.StepStatus.WAITING) {
			Logger.debug("Step routing clearing because step status is terminal connection=%s pipeline=%s stage=%s step=%s status=%s",
					connectionId, signal.getPipelineType(), stageId(signal), stepName(signal), result.getStatus());
			return RoutingPlan.clear(connectionId);
		}

		Routing.Target target = resolveStepTarget(signal);
		if (isBlank(target.getTarget())) {
			Logger.debug("Step routing clearing because target is missing connection=%s pipeline=%s stage=%s step=%s",
					connectionId, signal.getPipelineType(), stageId(signal), stepName(signal));
			return RoutingPlan.clear(connectionId);
		}

		Logger.debug("Step routing planned connection=%s pipeline=%s stage=%s step=%s target=%s",
				connectionId, signal.getPipelineType(), stageId(signal), stepName(signal), target.getTarget());
		return RoutingPlan.replace(createIntent(signal, connectionId, target, RoutingReason.STEP));
	}

	private @NotNull RoutingPlan planPipeline(@NotNull RoutingSignal signal, @NotNull UUID connectionId) {
		PipelineResult result = signal.getPipelineResult();
		if (result == null) {
			Logger.debug("Completion routing ignored because pipeline result is missing connection=%s pipeline=%s",
					connectionId, signal.getPipelineType());
			return RoutingPlan.ignore();
		}
		if (result.getStatus() == PipelineStatus.WAITING) {
			Logger.debug("Completion routing ignored for waiting pipeline connection=%s pipeline=%s",
					connectionId, signal.getPipelineType());
			return RoutingPlan.ignore();
		}
		if (result.getStatus() != PipelineStatus.COMPLETE) {
			Logger.debug("Completion routing clearing because pipeline status is terminal connection=%s pipeline=%s status=%s",
					connectionId, signal.getPipelineType(), result.getStatus());
			return RoutingPlan.clear(connectionId);
		}

		Routing.Target target = resolveCompletionTarget(signal);
		if (isBlank(target.getTarget())) {
			Logger.debug("Completion routing clearing because target is missing connection=%s pipeline=%s",
					connectionId, signal.getPipelineType());
			return RoutingPlan.clear(connectionId);
		}

		Logger.debug("Completion routing planned connection=%s pipeline=%s target=%s",
				connectionId, signal.getPipelineType(), target.getTarget());
		return RoutingPlan.replace(createIntent(signal, connectionId, target, RoutingReason.COMPLETION));
	}

	private @NotNull RoutingIntent createIntent(
			@NotNull RoutingSignal signal,
			@NotNull UUID connectionId,
			@NotNull Routing.Target target,
			@NotNull RoutingReason reason
	) {
		String providerId = signal.getContext().getProvider() != null
				? signal.getContext().getProvider().getProviderId()
				: null;
		String stepName = signal.getStep() != null ? signal.getStep().getName() : null;
		long now = System.currentTimeMillis();
		return new RoutingIntent(
				UUID.randomUUID(),
				connectionId,
				new RoutingEndpoint(target.getTarget().trim()),
				reason,
				copyPolicy(target.getAttempts(), reason),
				new RoutingAttemptState(),
				signal.getPipelineType(),
				signal.getStage(),
				providerId,
				stepName,
				now
		);
	}

	private Routing.Target resolveCompletionTarget(@NotNull RoutingSignal signal) {
		Routing routing = routingProvider.get();
		Routing.Target target = routing.getDefaults().getComplete();
		Routing.Targets scenario = resolveScenarioTargets(routing, signal.getPipelineType());
		return mergeTarget(target, scenario != null ? scenario.getComplete() : null);
	}

	private Routing.Target resolveStepTarget(@NotNull RoutingSignal signal) {
		Routing routing = routingProvider.get();
		Routing.Target target = routing.getDefaults().getStep();
		Routing.Targets scenario = resolveScenarioTargets(routing, signal.getPipelineType());
		target = mergeTarget(target, scenario != null ? scenario.getStep() : null);

		Routing.Targets.Overrides overrides = scenario != null ? scenario.getOverrides() : null;
		Routing.Target stageOverride = resolveStageOverride(overrides, signal.getStage() != null ? signal.getStage().id() : null);
		target = mergeTarget(target, stageOverride);

		Routing.Target stepOverride = resolveStepOverride(overrides, signal.getStep() != null ? signal.getStep().getName() : null);
		target = mergeTarget(target, stepOverride);

		return target;
	}

	private Routing.Targets resolveScenarioTargets(Routing routing, PipelineType pipelineType) {
		if (routing == null || pipelineType == null) return null;

		String scenarioId = pipelineType.name().toLowerCase(Locale.ROOT);
		for (Map.Entry<String, Routing.Targets> entry : routing.getScenarios().entrySet()) {
			if (entry.getKey() == null || entry.getValue() == null) continue;
			if (entry.getKey().equalsIgnoreCase(scenarioId))
				return entry.getValue();
		}

		return null;
	}

	private Routing.Target resolveStageOverride(@Nullable Routing.Targets.Overrides overrides, @Nullable String stageId) {
		if (overrides == null || stageId == null) return null;
		return resolveOverride(overrides.getStages(), stageId);
	}

	private Routing.Target resolveStepOverride(@Nullable Routing.Targets.Overrides overrides, @Nullable String stepName) {
		if (overrides == null || stepName == null) return null;
		return resolveOverride(overrides.getSteps(), stepName);
	}

	private Routing.Target resolveOverride(Map<String, Routing.Target> overrides, String key) {
		for (Map.Entry<String, Routing.Target> entry : overrides.entrySet()) {
			if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(key))
				return entry.getValue();
		}
		return null;
	}

	private @NotNull RoutingAttemptPolicy copyPolicy(@Nullable RoutingAttemptPolicy source, @NotNull RoutingReason reason) {
		RoutingAttemptPolicy fallback = reason == RoutingReason.COMPLETION
				? RoutingAttemptPolicy.defaultCompletion()
				: RoutingAttemptPolicy.defaultStep();
		if (source == null) return fallback;

		RoutingAttemptPolicy copy = new RoutingAttemptPolicy();
		copy.setMode(source.getMode());
		copy.setMaxAttempts(source.getMaxAttempts());
		copy.setRetryDelay(source.getRetryDelay());
		copy.setConsumeOnReached(source.isConsumeOnReached());
		copy.setConsumeOnExhausted(source.isConsumeOnExhausted());
		return copy;
	}

	private @NotNull Routing.Target mergeTarget(
			@Nullable Routing.Target base,
			@Nullable Routing.Target override
	) {
		Routing.Target merged = new Routing.Target();
		String baseTarget = base != null ? base.getTarget() : "";
		RoutingAttemptPolicy baseAttempts = base != null ? base.getAttempts() : null;
		if (override == null) {
			merged.setTarget(baseTarget);
			merged.setAttempts(baseAttempts);
			return merged;
		}

		merged.setTarget(!isBlank(override.getTarget()) ? override.getTarget() : baseTarget);
		merged.setAttempts(override.getAttempts() != null ? override.getAttempts() : baseAttempts);
		return merged;
	}

	private boolean isBlank(String value) {
		return value == null || value.isBlank();
	}

	private String stageId(@NotNull RoutingSignal signal) {
		return signal.getStage() != null ? signal.getStage().id() : null;
	}

	private String stepName(@NotNull RoutingSignal signal) {
		return signal.getStep() != null ? signal.getStep().getName() : null;
	}
}
