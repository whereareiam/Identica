package me.whereareiam.identica.trait.authoritative.username;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.trait.authoritative.username.config.provider.AuthoritativeUsernameMessagesProvider;
import me.whereareiam.identica.trait.authoritative.username.config.provider.UsernameConflictsProvider;
import me.whereareiam.identica.trait.authoritative.username.conflict.UsernameConflictType;
import me.whereareiam.identica.trait.authoritative.username.conflict.factory.UsernameConflictContextFactory;
import me.whereareiam.identica.trait.authoritative.username.conflict.resolver.UsernameConflictResolver;
import me.whereareiam.identica.trait.authoritative.username.model.AuthoritativeUsernameMessages;
import me.whereareiam.identica.trait.authoritative.username.pipeline.AuthoritativeUsernamePipelineExtension;
import me.whereareiam.identica.trait.authoritative.username.pipeline.prepare.group.policy.phase.ApplyAuthoritativeUsernamePhase;
import me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.authentication.group.identity.phase.SynchronizeAuthenticationUsernamePhase;
import me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.authentication.group.policy.phase.PersistAuthenticationUsernamePhase;
import me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.authentication.group.policy.phase.ReviewAuthenticationUsernamePhase;
import me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.migration.group.identity.phase.SynchronizeMigrationUsernamePhase;
import me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.registration.group.identity.phase.SynchronizeRegistrationUsernamePhase;
import me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.registration.group.policy.phase.PersistRegistrationUsernamePhase;
import me.whereareiam.identica.trait.authoritative.username.pipeline.scenario.type.registration.group.policy.phase.ReviewRegistrationUsernamePhase;

import java.nio.file.Path;

@RequiredArgsConstructor
public class UsernameModule extends AbstractModule {
	private final Path featurePath;

	@Override
	protected void configure() {
		bind(UsernameConflictsProvider.class).asEagerSingleton();
		bind(AuthoritativeUsernameMessagesProvider.class).asEagerSingleton();
		bind(AuthoritativeUsernameMessages.class).toProvider(AuthoritativeUsernameMessagesProvider.class);

		bind(UsernameConflictContextFactory.class).asEagerSingleton();
		bind(UsernameConflictResolver.class).asEagerSingleton();
		bind(UsernameConflictType.class).asEagerSingleton();

		bind(ApplyAuthoritativeUsernamePhase.class).asEagerSingleton();
		bind(SynchronizeAuthenticationUsernamePhase.class).asEagerSingleton();
		bind(SynchronizeRegistrationUsernamePhase.class).asEagerSingleton();
		bind(SynchronizeMigrationUsernamePhase.class).asEagerSingleton();
		bind(ReviewAuthenticationUsernamePhase.class).asEagerSingleton();
		bind(ReviewRegistrationUsernamePhase.class).asEagerSingleton();
		bind(PersistAuthenticationUsernamePhase.class).asEagerSingleton();
		bind(PersistRegistrationUsernamePhase.class).asEagerSingleton();
		bind(AuthoritativeUsernamePipelineExtension.class).asEagerSingleton();
	}

	@Provides
	@Singleton
	@Named("usernamePath")
	Path provideFeaturePath() {
		return featurePath;
	}
}
