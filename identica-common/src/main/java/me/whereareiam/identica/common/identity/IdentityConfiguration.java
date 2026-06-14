package me.whereareiam.identica.common.identity;

import com.google.inject.AbstractModule;
import me.whereareiam.identica.common.identity.account.DefaultAccountService;
import me.whereareiam.identica.common.identity.account.DefaultRegistrationAccountService;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.identity.account.RegistrationAccountService;

public class IdentityConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(AccountService.class).to(DefaultAccountService.class).asEagerSingleton();
		bind(RegistrationAccountService.class).to(DefaultRegistrationAccountService.class).asEagerSingleton();
		bind(IdentityService.class).to(DefaultIdentityService.class).asEagerSingleton();
	}
}
