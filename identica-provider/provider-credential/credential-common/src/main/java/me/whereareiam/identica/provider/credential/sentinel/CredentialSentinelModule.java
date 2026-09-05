package me.whereareiam.identica.provider.credential.sentinel;

import com.google.inject.AbstractModule;

public class CredentialSentinelModule extends AbstractModule {
	@Override
	protected void configure() {
		bind(BruteForceSentinelDefinition.class).asEagerSingleton();
		bind(BruteForceSentinelLifecycle.class).asEagerSingleton();
	}
}
