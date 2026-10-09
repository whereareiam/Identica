package me.whereareiam.identica.common.provider;

import com.google.inject.AbstractModule;
import me.whereareiam.identica.common.provider.reader.DefaultProviderDescriptorReader;
import me.whereareiam.identica.provider.ProviderDescriptorReader;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;

public class ProviderConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(ProviderDescriptorReader.class).to(DefaultProviderDescriptorReader.class).asEagerSingleton();
		bind(ProviderManager.class).to(DefaultProviderManager.class).asEagerSingleton();
		bind(ProviderOperations.class).to(DefaultProviderOperations.class).asEagerSingleton();
	}
}
