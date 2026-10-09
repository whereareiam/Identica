package me.whereareiam.identica.platform.bungeecord;

import com.google.inject.Guice;
import com.google.inject.Injector;
import me.whereareiam.attache.platform.bungeecord.BungeeCordLibraryManager;
import me.whereareiam.attache.type.VerbosityMode;
import me.whereareiam.identica.adapter.command.CommandConfiguration;
import me.whereareiam.identica.adapter.database.DatabaseConfiguration;
import me.whereareiam.identica.adapter.replication.ReplicationConfiguration;
import me.whereareiam.identica.common.CommonConfiguration;
import me.whereareiam.identica.common.Identica;
import me.whereareiam.identica.engine.EngineConfiguration;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.lifecycle.IdenticaBootstrappedEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaReadyEvent;
import me.whereareiam.identica.event.lifecycle.IdenticaShutdownEvent;
import me.whereareiam.identica.common.feature.FeatureRuntime;
import me.whereareiam.identica.feature.BuiltinFeatures;
import me.whereareiam.identica.trait.authoritative.username.UsernameConfiguration;
import com.google.inject.Module;
import java.util.ArrayList;
import java.util.List;
import me.whereareiam.identica.platform.bungeecord.logging.BungeeCordLoggingHelper;
import me.whereareiam.identica.type.PluginType;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.plugin.Plugin;

public class BungeeCordIdentica extends Plugin {
	private Injector injector;
	private BungeeAudiences audiences;
	private FeatureRuntime featureRuntime;

	@Override
	public void onEnable() {
		PluginType.setPluginType(PluginType.BUNGEECORD);
		BungeeCordLoggingHelper.setLogger(getLogger());

		BungeeCordLibraryManager libraryManager = new BungeeCordLibraryManager(this, ".libraries");
		libraryManager.setVerbosityMode(VerbosityMode.SUMMARY);
		libraryManager.loadDescriptors();

		audiences = BungeeAudiences.builder(this).build();
		featureRuntime = BuiltinFeatures.runtime(getDataFolder().toPath());
		List<Module> modules = new ArrayList<>(List.of(
				new CommonConfiguration(getDataFolder().toPath()),
				new UsernameConfiguration(getDataFolder().toPath().resolve("identity/username")),
				new EngineConfiguration(),
				new BungeeCordConfiguration(getProxy(), this, getDataFolder().toPath(), audiences),
				new CommandConfiguration(),
				new DatabaseConfiguration(),
				new ReplicationConfiguration()
		));
		modules.addAll(featureRuntime.modules());
		try {
			injector = Guice.createInjector(modules);
		} catch (RuntimeException | Error failure) {
			try {
				featureRuntime.shutdown();
			} catch (RuntimeException | Error cleanupFailure) {
				failure.addSuppressed(cleanupFailure);
			}
			throw failure;
		}
		EventManager eventManager = injector.getInstance(EventManager.class);
		try {
			injector.getInstance(Identica.class).bootstrap();
			featureRuntime.initialize(injector);
			eventManager.call(new IdenticaBootstrappedEvent());
			eventManager.call(new IdenticaReadyEvent());
		} catch (RuntimeException | Error failure) {
			eventManager.call(new IdenticaShutdownEvent());
			throw failure;
		}
	}

	@Override
	public void onDisable() {
		try {
			if (injector != null) injector.getInstance(EventManager.class).call(new IdenticaShutdownEvent());
		} finally {
			try {
				if (featureRuntime != null) featureRuntime.shutdown();
			} finally {
				if (audiences != null) audiences.close();
			}
		}
	}
}
