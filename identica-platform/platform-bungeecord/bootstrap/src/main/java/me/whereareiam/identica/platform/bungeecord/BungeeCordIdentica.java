package me.whereareiam.identica.platform.bungeecord;

import com.google.inject.Guice;
import com.google.inject.Injector;
import me.whereareiam.attache.platform.bungeecord.BungeeCordLibraryManager;
import me.whereareiam.attache.type.VerbosityMode;
import me.whereareiam.identica.adapter.command.CommandConfiguration;
import me.whereareiam.identica.adapter.database.DatabaseConfiguration;
import me.whereareiam.identica.adapter.replication.ReplicationConfiguration;
import me.whereareiam.identica.common.CommonConfiguration;
import me.whereareiam.identica.engine.EngineConfiguration;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.lifecycle.IdenticaBootstrappedEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaReadyEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.platform.bungeecord.logging.BungeeCordLoggingHelper;
import me.whereareiam.identica.type.PluginType;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.plugin.Plugin;

public class BungeeCordIdentica extends Plugin {
	private Injector injector;
	private BungeeAudiences audiences;

	@Override
	public void onEnable() {
		PluginType.setPluginType(PluginType.BUNGEECORD);
		BungeeCordLoggingHelper.setLogger(getLogger());

		BungeeCordLibraryManager libraryManager = new BungeeCordLibraryManager(this, ".libraries");
		libraryManager.setVerbosityMode(VerbosityMode.SUMMARY);
		libraryManager.loadDescriptors();

		audiences = BungeeAudiences.builder(this).build();
		injector = Guice.createInjector(
				new CommonConfiguration(getDataFolder().toPath()),
				new EngineConfiguration(),
				new BungeeCordConfiguration(getProxy(), this, getDataFolder().toPath(), audiences),
				new CommandConfiguration(),
				new DatabaseConfiguration(),
				new ReplicationConfiguration()
		);
		EventManager eventManager = injector.getInstance(EventManager.class);
		eventManager.call(new IdenticaBootstrappedEvent());
		eventManager.call(new IdenticaReadyEvent());
	}

	@Override
	public void onDisable() {
		if (injector != null) injector.getInstance(EventManager.class).call(new IdenticaShutdownEvent());
		if (audiences != null) audiences.close();
	}
}
