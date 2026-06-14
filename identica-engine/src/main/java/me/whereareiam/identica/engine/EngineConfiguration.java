package me.whereareiam.identica.engine;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import me.whereareiam.identica.connection.ConnectionCoordinator;
import me.whereareiam.identica.engine.pipeline.DefaultPipelineExtensionRegistry;
import me.whereareiam.identica.engine.pipeline.DefaultPipelineStateStore;
import me.whereareiam.identica.engine.pipeline.completion.extension.DefaultCompletionExtensionRegistry;
import me.whereareiam.identica.engine.pipeline.completion.lifecycle.CompletionPendingLifecycle;
import me.whereareiam.identica.engine.pipeline.completion.registry.CompletionPipelineRegistry;
import me.whereareiam.identica.engine.pipeline.completion.runtime.CompletionPipeline;
import me.whereareiam.identica.engine.pipeline.prepare.registry.PreparePipelineRegistry;
import me.whereareiam.identica.engine.pipeline.scenario.PendingPipelineKickCoordinator;
import me.whereareiam.identica.engine.pipeline.scenario.PendingScenarioAccountLifecycle;
import me.whereareiam.identica.engine.pipeline.scenario.ScenarioRegistry;
import me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.rule.DefaultJourneyRuleRegistry;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.AuthenticationPipelineRegistry;
import me.whereareiam.identica.engine.pipeline.scenario.type.authentication.DefaultAuthenticationStageRegistry;
import me.whereareiam.identica.engine.pipeline.scenario.type.migration.DefaultMigrationStageRegistry;
import me.whereareiam.identica.engine.pipeline.scenario.type.migration.MigrationPipelineRegistry;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.DefaultRegistrationStageRegistry;
import me.whereareiam.identica.engine.pipeline.scenario.type.registration.RegistrationPipelineRegistry;
import me.whereareiam.identica.pipeline.PipelineRegistry;
import me.whereareiam.identica.pipeline.completion.extension.CompletionExtensionRegistry;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.AuthenticationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.MigrationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.RegistrationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.rule.JourneyRuleRegistry;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;

public class EngineConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(PipelineStateStore.class).to(DefaultPipelineStateStore.class).asEagerSingleton();

		bind(PipelineRegistry.class).annotatedWith(Names.named("authenticationPipelineRegistry"))
				.to(AuthenticationPipelineRegistry.class)
				.asEagerSingleton();
		bind(PipelineRegistry.class).annotatedWith(Names.named("preparePipelineRegistry"))
				.to(PreparePipelineRegistry.class)
				.asEagerSingleton();
		bind(PipelineRegistry.class).annotatedWith(Names.named("registrationPipelineRegistry"))
				.to(RegistrationPipelineRegistry.class)
				.asEagerSingleton();
		bind(PipelineRegistry.class).annotatedWith(Names.named("migrationPipelineRegistry"))
				.to(MigrationPipelineRegistry.class)
				.asEagerSingleton();
		bind(PipelineRegistry.class).to(AuthenticationPipelineRegistry.class).asEagerSingleton();
		bind(PipelineExtensionRegistry.class).to(DefaultPipelineExtensionRegistry.class).asEagerSingleton();
		bind(CompletionExtensionRegistry.class).to(DefaultCompletionExtensionRegistry.class).asEagerSingleton();
		bind(CompletionPipelineRegistry.class).asEagerSingleton();
		bind(CompletionPipeline.class).asEagerSingleton();
		bind(CompletionPendingLifecycle.class).asEagerSingleton();

		bind(ScenarioRegistry.class).asEagerSingleton();
		bind(ConnectionCoordinator.class).to(DefaultConnectionCoordinator.class).asEagerSingleton();
		bind(PendingPipelineKickCoordinator.class).asEagerSingleton();
		bind(PendingScenarioAccountLifecycle.class).asEagerSingleton();

		bind(AuthenticationJourneyRegistry.class).to(DefaultAuthenticationStageRegistry.class).asEagerSingleton();
		bind(RegistrationJourneyRegistry.class).to(DefaultRegistrationStageRegistry.class).asEagerSingleton();
		bind(MigrationJourneyRegistry.class).to(DefaultMigrationStageRegistry.class).asEagerSingleton();
		bind(JourneyRuleRegistry.class).to(DefaultJourneyRuleRegistry.class).asEagerSingleton();
	}
}
