package me.whereareiam.identica.provider.credential;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.provider.credential.account.AutoupgradeLifecycle;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.account.DefaultCredentialAccountService;
import me.whereareiam.identica.provider.credential.command.*;
import me.whereareiam.identica.provider.credential.config.CredentialCommands;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.provider.credential.config.provider.CredentialCommandsProvider;
import me.whereareiam.identica.provider.credential.config.provider.CredentialMessagesProvider;
import me.whereareiam.identica.provider.credential.config.provider.CredentialSettingsProvider;
import me.whereareiam.identica.provider.credential.listener.CredentialAccountClearListener;
import me.whereareiam.identica.provider.credential.migration.CredentialMigrationPrecheck;
import me.whereareiam.identica.provider.credential.resolver.CredentialSubjectResolver;
import me.whereareiam.identica.provider.credential.util.PasswordRules;
import me.whereareiam.identica.provider.migration.ProviderMigrationPrecheck;
import me.whereareiam.identica.provider.subject.SubjectResolver;

public class CommonConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(CredentialSettingsProvider.class).asEagerSingleton();
		bind(CredentialSettings.class).toProvider(CredentialSettingsProvider.class);

		bind(CredentialMessagesProvider.class).asEagerSingleton();
		bind(CredentialMessages.class).toProvider(CredentialMessagesProvider.class);

		bind(CredentialCommandsProvider.class).asEagerSingleton();
		bind(CredentialCommands.class)
				.annotatedWith(Names.named("credential"))
				.toProvider(CredentialCommandsProvider.class);
		bind(Commands.class)
				.annotatedWith(Names.named("credential"))
				.toProvider(CredentialCommandsProvider.class);

		bind(CredentialAccountService.class).to(DefaultCredentialAccountService.class).asEagerSingleton();
		bind(CredentialAccountClearListener.class).asEagerSingleton();
		bind(AutoupgradeLifecycle.class).asEagerSingleton();
		bind(PasswordRules.class).asEagerSingleton();

		Multibinder<Object> credentialCommandInstances = Multibinder.newSetBinder(
				binder(),
				Object.class,
				Names.named("credentialCommandInstances")
		);
		credentialCommandInstances.addBinding().to(PassCommand.class);
		credentialCommandInstances.addBinding().to(LoginCommand.class);
		credentialCommandInstances.addBinding().to(PasswordChangeCommand.class);
		credentialCommandInstances.addBinding().to(CredentialCommand.class);
		credentialCommandInstances.addBinding().to(ManagementCommand.class);

		Multibinder.newSetBinder(binder(), ProviderMigrationPrecheck.class)
				.addBinding()
				.to(CredentialMigrationPrecheck.class);
		Multibinder.newSetBinder(binder(), SubjectResolver.class)
				.addBinding()
				.to(CredentialSubjectResolver.class);
	}
}
