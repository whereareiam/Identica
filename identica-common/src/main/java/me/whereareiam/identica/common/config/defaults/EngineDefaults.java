package me.whereareiam.identica.common.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.type.pipeline.PipelineConcurrencyPolicy;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.JourneyPolicy;

import java.time.Duration;

@Singleton
public class EngineDefaults implements DefaultsProvider<Engine> {
	@Override
	public Engine supply(Engine engine) {
		Engine.Behavior behavior = new Engine.Behavior();
		behavior.setBridgeTtl(Duration.ofMinutes(10));
		behavior.setHandshakeInstructionTtl(Duration.ofMinutes(10));
		engine.setBehavior(behavior);

		Engine.Scenarios scenarios = new Engine.Scenarios();
		scenarios.setAuthentication(defaultAuthenticationScenario());
		scenarios.setRegistration(defaultRegistrationScenario());
		scenarios.setMigration(defaultMigrationScenario());
		engine.setScenarios(scenarios);
		return engine;
	}

	private Engine.Authentication defaultAuthenticationScenario() {
		Engine.Authentication scenario = new Engine.Authentication();
		scenario.setPipelineTtl(Duration.ofMinutes(5));
		scenario.setAdvanceLockTtl(Duration.ofSeconds(5));
		scenario.setAllowResume(true);
		scenario.setPipelineConcurrencyPolicy(PipelineConcurrencyPolicy.DENY_NEW);
		scenario.setJourneyMode(JourneyMode.SEAMLESS);
		scenario.setJourneyPolicy(JourneyPolicy.PREFER);
		return scenario;
	}

	private Engine.Registration defaultRegistrationScenario() {
		Engine.Registration scenario = new Engine.Registration();
		scenario.setPipelineTtl(Duration.ofMinutes(5));
		scenario.setAdvanceLockTtl(Duration.ofSeconds(5));
		scenario.setAllowResume(true);
		scenario.setPipelineConcurrencyPolicy(PipelineConcurrencyPolicy.DENY_NEW);
		scenario.setAutoSelectSingleProvider(false);
		scenario.setJourneyMode(JourneyMode.SEAMLESS);
		scenario.setJourneyPolicy(JourneyPolicy.PREFER);
		return scenario;
	}

	private Engine.Migration defaultMigrationScenario() {
		Engine.Migration scenario = new Engine.Migration();
		scenario.setPipelineTtl(Duration.ofMinutes(5));
		scenario.setAdvanceLockTtl(Duration.ofSeconds(5));
		scenario.setAllowResume(true);
		scenario.setJourneyMode(JourneyMode.INTERACTIVE);
		scenario.setJourneyPolicy(JourneyPolicy.PREFER);
		return scenario;
	}
}
