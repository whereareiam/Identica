package me.whereareiam.identica.common.provider;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.common.provider.reader.DefaultProviderDescriptorReader;
import me.whereareiam.identica.common.provider.selection.DefaultProviderSelector;
import me.whereareiam.identica.common.provider.selection.ProviderClaimSourceRegistry;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.provider.ProviderDescriptorReader;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.selection.ProviderClaimSource;
import me.whereareiam.identica.provider.selection.ProviderSelector;

public class ProviderConfiguration extends AbstractModule {
	@Override
	protected void configure() {
		bind(ProviderDescriptorReader.class).to(DefaultProviderDescriptorReader.class).asEagerSingleton();
		bind(ProviderManager.class).to(DefaultProviderManager.class).asEagerSingleton();
		bind(ProviderOperations.class).to(DefaultProviderOperations.class).asEagerSingleton();
		bind(ProviderSelector.class).to(DefaultProviderSelector.class).asEagerSingleton();
		bind(new TypeLiteral<Registry<ProviderClaimSource>>() {}).to(ProviderClaimSourceRegistry.class).asEagerSingleton();
		Multibinder.newSetBinder(binder(), RuntimeLifecycle.class).addBinding().to(ProviderLifecycle.class);
	}
}
