package me.whereareiam.identica.common.provider;

import com.google.inject.Inject;
import me.whereareiam.identica.common.provider.classloader.SharedLibraryClassLoaderFactory;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.provider.ProviderManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Loads the providers once the core and the features run, and unloads them when Identica stops, closing the
 * libraries they shared.
 */
public final class ProviderLifecycle implements RuntimeLifecycle {
	private final ProviderManager providers;
	private final SharedLibraryClassLoaderFactory sharedLibraries;

	@Inject
	public ProviderLifecycle(@NotNull ProviderManager providers, @NotNull SharedLibraryClassLoaderFactory sharedLibraries) {
		this.providers = providers;
		this.sharedLibraries = sharedLibraries;
	}

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.PROVIDERS;
	}

	@Override
	public void initialize() {
		providers.loadProviders();
	}

	@Override
	public void shutdown() {
		try {
			providers.unloadProviders();
		} finally {
			try {
				sharedLibraries.close();
			} catch (IOException exception) {
				Logger.warn("Failed to close shared provider libraries: %s", exception.getMessage());
			}
		}
	}
}
