package me.whereareiam.identica.feature.restriction.join;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.feature.restriction.join.command.JoinRestrictionCommand;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionCommands;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionMessages;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionSettings;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionSettingsProvider;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionCommandsProvider;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionMessagesProvider;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionProvidersProvider;
import me.whereareiam.identica.feature.restriction.join.pipeline.JoinPipelineExtension;
import me.whereareiam.identica.feature.restriction.join.pipeline.prepare.group.policy.phase.ApplyJoinRestrictionPhase;
import me.whereareiam.identica.feature.restriction.join.pipeline.scenario.type.authentication.group.identity.phase.EnforceAuthenticationJoinRestrictionPhase;
import me.whereareiam.identica.feature.restriction.join.pipeline.scenario.type.registration.group.identity.phase.EnforceRegistrationJoinRestrictionPhase;

import java.nio.file.Path;
import java.util.Set;

@RequiredArgsConstructor
public class JoinRestrictionModule extends AbstractModule {
	private final Path joinRestrictionFeaturePath;

	@Override
	protected void configure() {
		bind(JoinRestrictionSettingsProvider.class).asEagerSingleton();
		bind(JoinRestrictionSettings.class).toProvider(JoinRestrictionSettingsProvider.class);
		bind(JoinRestrictionProvidersProvider.class).asEagerSingleton();
		bind(JoinRestrictionCommandsProvider.class).asEagerSingleton();
		bind(JoinRestrictionMessagesProvider.class).asEagerSingleton();
		bind(JoinRestrictionMessages.class).toProvider(JoinRestrictionMessagesProvider.class);
		bind(JoinRestrictionTypeResolver.class).asEagerSingleton();
		bind(ApplyJoinRestrictionPhase.class).asEagerSingleton();
		bind(EnforceAuthenticationJoinRestrictionPhase.class).asEagerSingleton();
		bind(EnforceRegistrationJoinRestrictionPhase.class).asEagerSingleton();
		bind(JoinPipelineExtension.class).asEagerSingleton();
		bind(JoinRestrictionCommand.class).asEagerSingleton();
		bind(CommandRegistrar.class).asEagerSingleton();
	}

	@Provides
	@Singleton
	@Named("joinRestrictionFeaturePath")
	Path provideJoinRestrictionFeaturePath() {
		return joinRestrictionFeaturePath;
	}

	@Provides
	@Singleton
	@Named("joinRestriction")
	JoinRestrictionCommands provideJoinRestrictionCommands(JoinRestrictionCommandsProvider provider) {
		return provider.get();
	}

	@Provides
	@Singleton
	@Named("joinRestrictionMessages")
	JoinRestrictionMessages provideJoinRestrictionMessages(JoinRestrictionMessagesProvider provider) {
		return provider.get();
	}

	@Provides
	@Singleton
	@Named("joinRestrictionCommandInstances")
	Set<Object> provideJoinRestrictionCommandInstances(JoinRestrictionCommand command) {
		return Set.of(command);
	}
}
