package me.whereareiam.identica.provider.premium;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import me.whereareiam.identica.handshake.policy.HandshakePolicy;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.provider.eligibility.ProviderEligibilityResolver;
import me.whereareiam.identica.provider.migration.ProviderMigrationPrecheck;
import me.whereareiam.identica.provider.premium.command.PremiumCommand;
import me.whereareiam.identica.provider.premium.config.PremiumCommands;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.provider.premium.config.PremiumSettings;
import me.whereareiam.identica.provider.premium.config.provider.PremiumCommandsProvider;
import me.whereareiam.identica.provider.premium.config.provider.PremiumMessagesProvider;
import me.whereareiam.identica.provider.premium.config.provider.PremiumSettingsProvider;
import me.whereareiam.identica.provider.premium.migration.PremiumMigrationPrecheck;
import me.whereareiam.identica.provider.premium.policy.PremiumHandshakePolicy;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileStore;
import me.whereareiam.identica.provider.premium.resolver.PremiumEligibilityResolver;
import me.whereareiam.identica.provider.premium.resolver.PremiumSubjectResolver;
import me.whereareiam.identica.provider.subject.SubjectResolver;
import me.whereareiam.identica.replication.store.participant.AccountLifecycleParticipant;

public class PremiumModule extends AbstractModule {
	@Override
	protected void configure() {
		// Configs
		bind(PremiumSettingsProvider.class).asEagerSingleton();
			bind(PremiumSettings.class).toProvider(PremiumSettingsProvider.class);
			bind(PremiumProfileStore.class).asEagerSingleton();
			Multibinder.newSetBinder(binder(), AccountLifecycleParticipant.class)
					.addBinding()
					.to(PremiumProfileStore.class);
			bind(PremiumMessagesProvider.class).asEagerSingleton();
		bind(PremiumMessages.class).toProvider(PremiumMessagesProvider.class);
		bind(PremiumCommandsProvider.class).asEagerSingleton();

		bind(PremiumCommands.class)
				.annotatedWith(Names.named("premium"))
				.toProvider(PremiumCommandsProvider.class);

		bind(Commands.class)
				.annotatedWith(Names.named("premium"))
				.toProvider(PremiumCommandsProvider.class);

		Multibinder.newSetBinder(binder(), Object.class, Names.named("premiumCommandInstances"))
				.addBinding()
				.to(PremiumCommand.class);

		Multibinder.newSetBinder(binder(), HandshakePolicy.class)
				.addBinding()
				.to(PremiumHandshakePolicy.class);
		Multibinder.newSetBinder(binder(), ProviderEligibilityResolver.class)
				.addBinding()
				.to(PremiumEligibilityResolver.class);
		Multibinder.newSetBinder(binder(), SubjectResolver.class)
				.addBinding()
				.to(PremiumSubjectResolver.class);
		Multibinder.newSetBinder(binder(), ProviderMigrationPrecheck.class)
				.addBinding()
				.to(PremiumMigrationPrecheck.class);
	}
}
