package me.whereareiam.identica.common.config;

import com.google.inject.AbstractModule;
import me.whereareiam.identica.common.config.provider.*;
import me.whereareiam.identica.config.ConfigCatalog;
import me.whereareiam.identica.model.config.*;
import me.whereareiam.identica.model.config.persistence.Persistence;
import me.whereareiam.identica.model.config.provider.Providers;

public class ConfigBindings extends AbstractModule {
	@Override
	protected void configure() {
		bind(Settings.class).toProvider(SettingsProvider.class);
		bind(Engine.class).toProvider(EngineProvider.class);
		bind(Routing.class).toProvider(RoutingProvider.class);
		bind(Messages.class).toProvider(MessagesProvider.class);
		bind(Commands.class).toProvider(CommandsProvider.class);
		bind(Providers.class).toProvider(ProvidersProvider.class);
		bind(Persistence.class).toProvider(PersistenceProvider.class);
		bind(Replication.class).toProvider(ReplicationProvider.class);

		bind(ConfigCatalog.class).to(DefaultConfigCatalog.class);
	}
}
