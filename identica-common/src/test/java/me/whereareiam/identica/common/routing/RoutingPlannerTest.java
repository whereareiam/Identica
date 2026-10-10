package me.whereareiam.identica.common.routing;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.config.Routing;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.ScenarioTransitionItem;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.routing.RoutingPlan;
import me.whereareiam.identica.model.routing.RoutingSignal;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.platform.adapter.PlatformForcedHostAdapter;
import me.whereareiam.identica.pipeline.journey.step.Step;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.StageType;
import me.whereareiam.identica.type.pipeline.journey.step.StepContextRequirement;
import me.whereareiam.identica.type.routing.RoutingPlanAction;
import me.whereareiam.identica.type.routing.RoutingRetryMode;
import me.whereareiam.identica.type.routing.reason.RoutingClearReason;
import me.whereareiam.identica.type.routing.reason.RoutingReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("Routing Planner")
class RoutingPlannerTest {
	private static final PlatformForcedHostAdapter NO_FORCED_HOSTS = connection -> Optional.empty();
	private static final PlatformForcedHostAdapter FORCED_HOSTS = connection -> Optional.ofNullable(connection.getOrigin())
			.map(origin -> Map.of("survival.example.com", "survival").get(origin.getHost()));

	@DisplayName("A waiting step produces a step-based routing intent")
	@Test
	void waitingStepCreatesStepIntent() {
		RoutingPlanner planner = new RoutingPlanner(this::settings, NO_FORCED_HOSTS);
		RoutingPlan plan = planner.plan(RoutingSignal.stepFinished(
				context(UUID.randomUUID()),
				PipelineType.AUTHENTICATION,
				StageType.PROVIDER,
				step("credential"),
				StepResult.waiting("")
		));

		assertEquals(RoutingPlanAction.REPLACE, plan.getAction());
		assertEquals(RoutingReason.STEP, plan.getIntent().getReason());
		assertEquals("auth", plan.getIntent().getEndpoint().getServer());
	}

	@DisplayName("A completed pipeline produces a completion routing intent")
	@Test
	void completedPipelineCreatesCompletionIntent() {
		RoutingPlanner planner = new RoutingPlanner(this::settings, NO_FORCED_HOSTS);
		RoutingPlan plan = planner.plan(RoutingSignal.pipelineFinished(
				context(UUID.randomUUID()),
				PipelineType.AUTHENTICATION,
				PipelineResult.complete()
		));

		assertEquals(RoutingPlanAction.REPLACE, plan.getAction());
		assertEquals(RoutingReason.COMPLETION, plan.getIntent().getReason());
		assertEquals("lobby", plan.getIntent().getEndpoint().getServer());
	}

	@DisplayName("Missing scenario targets fall back to routing defaults")
	@Test
	void missingScenarioTargetsUseDefaults() {
		Routing settings = settings();
		settings.getScenarios().clear();

		RoutingPlanner planner = new RoutingPlanner(() -> settings, NO_FORCED_HOSTS);
		RoutingPlan stepPlan = planner.plan(RoutingSignal.stepFinished(
				context(UUID.randomUUID()),
				PipelineType.REGISTRATION,
				StageType.PROVIDER,
				step("credential"),
				StepResult.waiting("")
		));
		RoutingPlan completionPlan = planner.plan(RoutingSignal.pipelineFinished(
				context(UUID.randomUUID()),
				PipelineType.REGISTRATION,
				PipelineResult.complete()
		));

		assertEquals("fallback-auth", stepPlan.getIntent().getEndpoint().getServer());
		assertEquals("fallback-lobby", completionPlan.getIntent().getEndpoint().getServer());
	}

	@DisplayName("Scenario completion targets inherit default attempts when omitted")
	@Test
	void completionTargetInheritsDefaultAttemptsWhenOmitted() {
		Routing settings = settings();
		settings.getDefaults().getComplete().getAttempts().setMode(RoutingRetryMode.UNTIL_REACHED);
		settings.getScenarios().get("authentication").getComplete().setAttempts(null);

		RoutingPlanner planner = new RoutingPlanner(() -> settings, NO_FORCED_HOSTS);
		RoutingPlan plan = planner.plan(RoutingSignal.pipelineFinished(
				context(UUID.randomUUID()),
				PipelineType.AUTHENTICATION,
				PipelineResult.complete()
		));

		assertEquals(RoutingRetryMode.UNTIL_REACHED, plan.getIntent().getAttemptPolicy().getMode());
	}

	@DisplayName("A failed pipeline clears the current routing intent")
	@Test
	void failedPipelineClearsIntent() {
		UUID connectionId = UUID.randomUUID();
		RoutingPlanner planner = new RoutingPlanner(this::settings, NO_FORCED_HOSTS);
		RoutingPlan plan = planner.plan(RoutingSignal.pipelineFinished(
				context(connectionId),
				PipelineType.AUTHENTICATION,
				PipelineResult.failed("no")
		));

		assertEquals(RoutingPlanAction.CLEAR, plan.getAction());
		assertEquals(connectionId, plan.getConnectionUniqueId());
		assertEquals(RoutingClearReason.PIPELINE_FAILED, plan.getClearReason());
	}

	@DisplayName("Step-specific overrides win over stage and scenario defaults")
	@Test
	void stepOverrideBeatsStageAndScenarioDefaults() {
		Routing settings = settings();
		Routing.Target stageTarget = Routing.Target.step();
		stageTarget.setTarget("stage-server");
		Routing.Target stepTarget = Routing.Target.step();
		stepTarget.setTarget("step-server");
		Routing.Targets targets = settings.getScenarios().get("authentication");
		targets.getOverrides().getStages().put(StageType.PROVIDER.id(), stageTarget);
		targets.getOverrides().getSteps().put("credential", stepTarget);

		RoutingPlanner planner = new RoutingPlanner(() -> settings, NO_FORCED_HOSTS);
		RoutingPlan plan = planner.plan(RoutingSignal.stepFinished(
				context(UUID.randomUUID()),
				PipelineType.AUTHENTICATION,
				StageType.PROVIDER,
				step("credential"),
				StepResult.waiting("")
		));

		assertEquals("step-server", plan.getIntent().getEndpoint().getServer());
	}

	@DisplayName("Blank routing targets clear the current intent")
	@Test
	void blankTargetClearsIntent() {
		Routing settings = settings();
		settings.getScenarios().get("authentication").getStep().setTarget("");
		settings.getDefaults().getStep().setTarget("");

		RoutingPlanner planner = new RoutingPlanner(() -> settings, NO_FORCED_HOSTS);
		RoutingPlan plan = planner.plan(RoutingSignal.stepFinished(
				context(UUID.randomUUID()),
				PipelineType.AUTHENTICATION,
				StageType.PROVIDER,
				step("credential"),
				StepResult.waiting("")
		));

		assertEquals(RoutingPlanAction.CLEAR, plan.getAction());
		assertEquals(RoutingClearReason.NO_TARGET, plan.getClearReason());
	}

	@DisplayName("A target that follows forced hosts routes to the proxy's forced host")
	@Test
	void forcedHostReplacesTarget() {
		Routing settings = settings();
		settings.getScenarios().get("authentication").getComplete().setForcedHosts(true);

		RoutingPlanner planner = new RoutingPlanner(() -> settings, FORCED_HOSTS);
		RoutingPlan plan = planner.plan(RoutingSignal.pipelineFinished(
				context(UUID.randomUUID(), "survival.example.com"),
				PipelineType.AUTHENTICATION,
				PipelineResult.complete()
		));

		assertEquals(RoutingPlanAction.REPLACE, plan.getAction());
		assertEquals("survival", plan.getIntent().getEndpoint().getServer());
	}

	@DisplayName("A hostname without a forced host falls back to the target")
	@Test
	void hostnameWithoutForcedHostUsesTarget() {
		Routing settings = settings();
		settings.getScenarios().get("authentication").getComplete().setForcedHosts(true);

		RoutingPlanner planner = new RoutingPlanner(() -> settings, FORCED_HOSTS);
		RoutingPlan otherHost = planner.plan(RoutingSignal.pipelineFinished(
				context(UUID.randomUUID(), "play.example.com"),
				PipelineType.AUTHENTICATION,
				PipelineResult.complete()
		));
		RoutingPlan unknownHost = planner.plan(RoutingSignal.pipelineFinished(
				context(UUID.randomUUID()),
				PipelineType.AUTHENTICATION,
				PipelineResult.complete()
		));

		assertEquals("lobby", otherHost.getIntent().getEndpoint().getServer());
		assertEquals("lobby", unknownHost.getIntent().getEndpoint().getServer());
	}

	@DisplayName("Forced hosts are ignored unless the target follows them")
	@Test
	void forcedHostIgnoredWhenTargetDoesNotFollowIt() {
		RoutingPlanner planner = new RoutingPlanner(this::settings, FORCED_HOSTS);
		RoutingPlan plan = planner.plan(RoutingSignal.pipelineFinished(
				context(UUID.randomUUID(), "survival.example.com"),
				PipelineType.AUTHENTICATION,
				PipelineResult.complete()
		));

		assertEquals("lobby", plan.getIntent().getEndpoint().getServer());
	}

	@DisplayName("A scenario target inherits and overrides whether defaults follow forced hosts")
	@Test
	void scenarioTargetInheritsAndOverridesForcedHosts() {
		Routing settings = settings();
		settings.getDefaults().getComplete().setForcedHosts(true);
		Routing.Targets registration = new Routing.Targets();
		registration.getComplete().setTarget("tutorial");
		registration.getComplete().setForcedHosts(false);
		settings.getScenarios().put("registration", registration);

		RoutingPlanner planner = new RoutingPlanner(() -> settings, FORCED_HOSTS);
		RoutingPlan inherited = planner.plan(RoutingSignal.pipelineFinished(
				context(UUID.randomUUID(), "survival.example.com"),
				PipelineType.AUTHENTICATION,
				PipelineResult.complete()
		));
		RoutingPlan overridden = planner.plan(RoutingSignal.pipelineFinished(
				context(UUID.randomUUID(), "survival.example.com"),
				PipelineType.REGISTRATION,
				PipelineResult.complete()
		));

		assertEquals("survival", inherited.getIntent().getEndpoint().getServer());
		assertEquals("tutorial", overridden.getIntent().getEndpoint().getServer());
	}

	@DisplayName("A blank target that follows forced hosts only routes hostnames with a forced host")
	@Test
	void blankTargetFollowingForcedHostsRoutesOnlyForcedHostnames() {
		Routing settings = settings();
		Routing.Target complete = settings.getScenarios().get("authentication").getComplete();
		complete.setTarget("");
		complete.setForcedHosts(true);
		settings.getDefaults().getComplete().setTarget("");

		RoutingPlanner planner = new RoutingPlanner(() -> settings, FORCED_HOSTS);
		RoutingPlan forced = planner.plan(RoutingSignal.pipelineFinished(
				context(UUID.randomUUID(), "survival.example.com"),
				PipelineType.AUTHENTICATION,
				PipelineResult.complete()
		));
		RoutingPlan unforced = planner.plan(RoutingSignal.pipelineFinished(
				context(UUID.randomUUID(), "play.example.com"),
				PipelineType.AUTHENTICATION,
				PipelineResult.complete()
		));

		assertEquals("survival", forced.getIntent().getEndpoint().getServer());
		assertEquals(RoutingPlanAction.CLEAR, unforced.getAction());
		assertEquals(RoutingClearReason.NO_TARGET, unforced.getClearReason());
	}

	private Routing settings() {
		Routing settings = new Routing();
		Routing.Defaults defaults = new Routing.Defaults();
		Routing.Target defaultStep = Routing.Target.step();
		defaultStep.setTarget("fallback-auth");
		Routing.Target defaultComplete = Routing.Target.complete();
		defaultComplete.setTarget("fallback-lobby");
		defaults.setStep(defaultStep);
		defaults.setComplete(defaultComplete);

		Routing.Targets authentication = new Routing.Targets();
		Routing.Target step = Routing.Target.step();
		step.setTarget("auth");
		Routing.Target complete = Routing.Target.complete();
		complete.setTarget("lobby");
		authentication.setStep(step);
		authentication.setComplete(complete);
		authentication.setOverrides(new Routing.Targets.Overrides());
		authentication.getOverrides().setStages(new HashMap<>());
		authentication.getOverrides().setSteps(new HashMap<>());

		settings.setDefaults(defaults);
		settings.getScenarios().put("authentication", authentication);
		return settings;
	}

	private ScenarioContext context(UUID connectionId) {
		return context(connectionId, null);
	}

	private ScenarioContext context(UUID connectionId, @Nullable String host) {
		ConnectionIdentity identity = new ConnectionIdentity(connectionId, "PlayerOne", "127.0.0.1");
		if (host != null) identity.setOrigin(new ConnectionIdentity.Origin(host, 25565));

		return new ScenarioContext() {
			private ProviderContext provider;
			private ScenarioTransitionItem transition;

			@Override
			public @Nullable UUID getConnectionUniqueId() {
				return connectionId;
			}

			@Override
			public @NotNull ConnectionIdentity getIdentity() {
				return identity;
			}

			@Override
			public @Nullable String getIntendedServer() {
				return null;
			}

			@Override
			public @Nullable ProviderContext getProvider() {
				return provider;
			}

			@Override
			public void setProvider(@Nullable ProviderContext provider) {
				this.provider = provider;
			}

			@Override
			public @Nullable ScenarioTransitionItem getTransition() {
				return transition;
			}

			@Override
			public void setTransition(@Nullable ScenarioTransitionItem transition) {
				this.transition = transition;
			}
		};
	}

	private Step step(String name) {
		return new Step() {
			@Override
			public @NotNull String getName() {
				return name;
			}

			@Override
			public @NotNull Set<JourneyMode> journeyModes() {
				return Set.of(JourneyMode.SEAMLESS, JourneyMode.INTERACTIVE);
			}

			@Override
			public @NotNull StepContextRequirement contextRequirement() {
				return StepContextRequirement.LOGIN;
			}

			@Override
			public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
				return CompletableFuture.completedFuture(StepResult.waiting(""));
			}
		};
	}
}
