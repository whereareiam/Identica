package me.whereareiam.identica.common.identity;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import me.whereareiam.identica.common.config.provider.UsernameConflictsProvider;
import me.whereareiam.identica.common.config.provider.UsernameMessagesProvider;
import me.whereareiam.identica.common.identity.account.DefaultAccountService;
import me.whereareiam.identica.common.identity.account.DefaultRegistrationAccountService;
import me.whereareiam.identica.common.identity.username.DefaultUsernameAssigner;
import me.whereareiam.identica.common.identity.username.UsernameLifecycle;
import me.whereareiam.identica.common.identity.username.UsernameOutcomeCheck;
import me.whereareiam.identica.common.conflict.ConflictDocument;
import me.whereareiam.identica.common.migration.DefaultMigrationService;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.identity.account.RegistrationAccountService;
import me.whereareiam.identica.identity.username.UsernameAssigner;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.model.config.UsernameConflicts;
import me.whereareiam.identica.model.config.UsernameMessages;
import me.whereareiam.identica.service.MigrationService;

public class IdentityConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(AccountService.class).to(DefaultAccountService.class).asEagerSingleton();
		bind(RegistrationAccountService.class).to(DefaultRegistrationAccountService.class).asEagerSingleton();
		bind(MigrationService.class).to(DefaultMigrationService.class).asEagerSingleton();
		bind(IdentityService.class).to(DefaultIdentityService.class).asEagerSingleton();

		bind(UsernameConflictsProvider.class).asEagerSingleton();
		bind(UsernameMessagesProvider.class).asEagerSingleton();
		bind(UsernameMessages.class).toProvider(UsernameMessagesProvider.class);
		bind(UsernameConflicts.class).toProvider(UsernameConflictsProvider.class);
		bind(UsernameAssigner.class).to(DefaultUsernameAssigner.class).asEagerSingleton();
		Multibinder<RuntimeLifecycle> lifecycles = Multibinder.newSetBinder(binder(), RuntimeLifecycle.class);
		lifecycles.addBinding().to(UsernameLifecycle.class);
		lifecycles.addBinding().to(UsernameOutcomeCheck.class);
		Multibinder.newSetBinder(binder(), ConflictDocument.class).addBinding().to(UsernameConflictsProvider.class);
	}
}
