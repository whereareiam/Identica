package me.whereareiam.identica.platform.bungeecord;

import com.google.inject.Guice;
import com.google.inject.Module;
import me.whereareiam.identica.adapter.command.CommandConfiguration;
import me.whereareiam.identica.adapter.database.DatabaseConfiguration;
import me.whereareiam.identica.adapter.replication.ReplicationConfiguration;
import me.whereareiam.identica.common.CommonConfiguration;
import me.whereareiam.identica.common.feature.FeatureRuntime;
import me.whereareiam.identica.engine.EngineConfiguration;
import me.whereareiam.identica.feature.BuiltinFeatures;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.PluginManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BungeeCord injector")
class BungeeCordInjectorTest {
	@TempDir
	Path directory;

	@DisplayName("Creating Identica's injector touches no file: documents, the database and the stores open during startup")
	@Test
	void createsTheInjectorWithoutIo() throws IOException {
		Path dataPath = directory.resolve("identica");

		try (FeatureRuntime features = BuiltinFeatures.runtime(dataPath)) {
			ProxyServer proxyServer = mock(ProxyServer.class, RETURNS_DEEP_STUBS);
			when(proxyServer.getLogger()).thenReturn(Logger.getLogger(getClass().getName()));
			// The listener registrar reads BungeeCord's event bus out of a real plugin manager.
			PluginManager pluginManager = new PluginManager(proxyServer);
			when(proxyServer.getPluginManager()).thenReturn(pluginManager);
			BungeeCordIdentica plugin = mock(BungeeCordIdentica.class);
			when(plugin.getProxy()).thenReturn(proxyServer);

			List<Module> modules = new ArrayList<>(List.of(
					new CommonConfiguration(dataPath),
					new EngineConfiguration(),
					new BungeeCordConfiguration(
							proxyServer,
							plugin,
							dataPath,
							mock(BungeeAudiences.class)
					),
					new CommandConfiguration(),
					new DatabaseConfiguration(),
					new ReplicationConfiguration()
			));
			modules.addAll(features.modules());

			Guice.createInjector(modules);
		}

		assertEquals(List.of(), written(), "The injector must not create the data directory or write a document");
	}

	private List<String> written() throws IOException {
		try (Stream<Path> paths = Files.walk(directory)) {
			return paths.filter(path -> !path.equals(directory)).map(path -> directory.relativize(path).toString()).sorted().toList();
		}
	}
}
