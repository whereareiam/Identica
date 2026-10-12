package me.whereareiam.identica.common.config;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import me.whereareiam.strata.Strata;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Prepares the data directory and loads Identica's configuration: creates the directories, brings the documents
 * of an older installation up to date through {@link ConfigMigrations}, installs the shared Configura instance and
 * loads every document in {@link ConfigLoadOrder}. A failed migration stops the startup, so Identica does not run
 * on a directory it could not upgrade.
 */
public final class ConfigurationLifecycle implements RuntimeLifecycle {
	private final Injector injector;
	private final Provider<Configura> configura;
	private final Provider<Strata> strata;
	private final Path dataPath;
	private final Path providersPath;
	private final Path featuresPath;

	@Inject
	public ConfigurationLifecycle(
			@NotNull Injector injector,
			@NotNull Provider<Configura> configura,
			@NotNull Provider<Strata> strata,
			@Named("dataPath") @NotNull Path dataPath,
			@Named("providersPath") @NotNull Path providersPath,
			@Named("featuresPath") @NotNull Path featuresPath
	) {
		this.injector = injector;
		this.configura = configura;
		this.strata = strata;
		this.dataPath = dataPath;
		this.providersPath = providersPath;
		this.featuresPath = featuresPath;
	}

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.CONFIGURATION;
	}

	@Override
	public void initialize() {
		for (Path directory : new Path[]{dataPath, providersPath, featuresPath})
			createDirectory(directory);

		strata.get().migrate();
		Config.setConfigured(configura.get());
		ConfigInitializer.initialize(injector);
	}

	/**
	 * Keeps the configuration installed: what stops after this lifecycle may still read its documents.
	 */
	@Override
	public void shutdown() {
	}

	private static void createDirectory(@NotNull Path directory) {
		try {
			Files.createDirectories(directory);
		} catch (IOException exception) {
			throw new UncheckedIOException("Failed to create the directory " + directory, exception);
		}
	}
}
