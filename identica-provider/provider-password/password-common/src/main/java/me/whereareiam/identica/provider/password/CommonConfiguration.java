package me.whereareiam.identica.provider.password;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Names;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.provider.password.account.AutoupgradeLifecycle;
import me.whereareiam.identica.provider.password.account.PasswordAccountService;
import me.whereareiam.identica.provider.password.account.DefaultPasswordAccountService;
import me.whereareiam.identica.provider.password.command.ChangePasswordCommand;
import me.whereareiam.identica.provider.password.command.PasswordCommand;
import me.whereareiam.identica.provider.password.command.LoginCommand;
import me.whereareiam.identica.provider.password.command.ManagementCommand;
import me.whereareiam.identica.provider.password.command.PassCommand;
import me.whereareiam.identica.provider.password.config.PasswordCommands;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.config.PasswordSettings;
import me.whereareiam.identica.provider.password.config.provider.PasswordCommandsProvider;
import me.whereareiam.identica.provider.password.config.provider.PasswordMessagesProvider;
import me.whereareiam.identica.provider.password.config.provider.PasswordSettingsProvider;
import me.whereareiam.identica.provider.password.listener.AccountClearPasswordListener;
import me.whereareiam.identica.provider.password.migration.PasswordMigrationPrecheck;
import me.whereareiam.identica.provider.password.sentinel.BruteForceSentinelLifecycle;
import me.whereareiam.identica.provider.password.sentinel.BruteForceSentinelDefinition;
import me.whereareiam.identica.provider.password.util.PasswordRules;
import me.whereareiam.identica.provider.migration.ProviderMigrationPrecheck;

public class CommonConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(PasswordSettingsProvider.class).asEagerSingleton();
		bind(PasswordSettings.class).toProvider(PasswordSettingsProvider.class);

		bind(PasswordMessagesProvider.class).asEagerSingleton();
		bind(PasswordMessages.class).toProvider(PasswordMessagesProvider.class);

		bind(PasswordCommandsProvider.class).asEagerSingleton();
		bind(PasswordCommands.class)
				.annotatedWith(Names.named("password"))
				.toProvider(PasswordCommandsProvider.class);
		bind(Commands.class)
				.annotatedWith(Names.named("password"))
				.toProvider(PasswordCommandsProvider.class);

		bind(PasswordAccountService.class).to(DefaultPasswordAccountService.class).asEagerSingleton();
		bind(AccountClearPasswordListener.class).asEagerSingleton();
		bind(AutoupgradeLifecycle.class).asEagerSingleton();
		bind(PasswordRules.class).asEagerSingleton();

		bind(BruteForceSentinelDefinition.class).asEagerSingleton();
		bind(BruteForceSentinelLifecycle.class).asEagerSingleton();

		Multibinder<Object> passwordCommandInstances = Multibinder.newSetBinder(
				binder(),
				Object.class,
				Names.named("passwordCommandInstances")
		);
		passwordCommandInstances.addBinding().to(PassCommand.class);
		passwordCommandInstances.addBinding().to(LoginCommand.class);
		passwordCommandInstances.addBinding().to(ChangePasswordCommand.class);
		passwordCommandInstances.addBinding().to(PasswordCommand.class);
		passwordCommandInstances.addBinding().to(ManagementCommand.class);

		Multibinder.newSetBinder(binder(), ProviderMigrationPrecheck.class)
				.addBinding()
				.to(PasswordMigrationPrecheck.class);
	}
}
