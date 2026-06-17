package me.whereareiam.identica.common.provider;

import com.google.inject.AbstractModule;
import me.whereareiam.identica.common.provider.capability.DefaultProviderCapabilityCoordinator;
import me.whereareiam.identica.common.provider.capability.DefaultProviderCapabilityRegistry;
import me.whereareiam.identica.common.provider.discovery.reader.DefaultProviderDescriptorReader;
import me.whereareiam.identica.provider.ProviderDescriptorReader;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.capability.ProviderCapabilityCoordinator;
import me.whereareiam.identica.provider.capability.ProviderCapabilityRegistry;

public class ProviderConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(ProviderDescriptorReader.class).to(DefaultProviderDescriptorReader.class).asEagerSingleton();
		bind(ProviderCapabilityCoordinator.class).to(DefaultProviderCapabilityCoordinator.class).asEagerSingleton();
		bind(ProviderCapabilityRegistry.class).to(DefaultProviderCapabilityRegistry.class).asEagerSingleton();
		bind(ProviderManager.class).to(DefaultProviderManager.class).asEagerSingleton();
		bind(ProviderOperations.class).to(DefaultProviderOperations.class).asEagerSingleton();
	}
}
