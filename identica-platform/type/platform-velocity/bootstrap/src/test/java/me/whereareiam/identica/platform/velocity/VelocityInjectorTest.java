package me.whereareiam.identica.platform.velocity;

import com.google.inject.Guice;
import com.google.inject.Module;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import me.whereareiam.attache.LibraryManager;
import me.whereareiam.identica.adapter.command.CommandConfiguration;
import me.whereareiam.identica.adapter.database.DatabaseConfiguration;
import me.whereareiam.identica.adapter.replication.ReplicationConfiguration;
import me.whereareiam.identica.common.CommonConfiguration;
import me.whereareiam.identica.common.feature.FeatureRuntime;
import me.whereareiam.identica.engine.EngineConfiguration;
import me.whereareiam.identica.feature.BuiltinFeatures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

@DisplayName("Velocity injector")
class VelocityInjectorTest {
	@TempDir
	Path directory;

	@DisplayName("Creating Identica's injector touches no file: documents, the database and the stores open during startup")
	@Test
	void createsTheInjectorWithoutIo() throws IOException {
		Path dataPath = directory.resolve("identica");

		try (FeatureRuntime features = BuiltinFeatures.runtime(dataPath)) {
			List<Module> modules = new ArrayList<>(List.of(
					new CommonConfiguration(dataPath),
					new EngineConfiguration(),
					new VelocityConfiguration(
							mock(ProxyServer.class, RETURNS_DEEP_STUBS),
							mock(VelocityIdentica.class),
							mock(PluginContainer.class, RETURNS_DEEP_STUBS),
							dataPath,
							mock(Logger.class),
							mock(LibraryManager.class)
					),
					new CommandConfiguration(),
					new DatabaseConfiguration(),
					new ReplicationConfiguration()
			));
			modules.addAll(features.modules());
			// Identica's packaged Guice is relocated and does not read Velocity's @DataDirectory as a binding
			// annotation, so bStats receives the plain data path there; this test runs on the unrelocated classes.
			modules.add(binder -> binder.bind(Path.class).annotatedWith(DataDirectory.class).toInstance(dataPath));

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
