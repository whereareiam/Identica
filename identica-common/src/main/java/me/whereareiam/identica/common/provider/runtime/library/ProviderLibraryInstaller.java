package me.whereareiam.identica.common.provider.runtime.library;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.attache.platform.standalone.StandaloneLibraryManager;
import me.whereareiam.attache.type.VerbosityMode;
import me.whereareiam.identica.common.provider.runtime.classloader.SharedCapabilityClassLoaderFactory;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.dependency.ProviderLibraries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URLClassLoader;
import java.nio.file.Path;

@Singleton
public class ProviderLibraryInstaller {
	private final Path providersPath;
	private final Path capabilitiesPath;
	private final ProviderLibraryLoggingAdapter loggingHelper;
	private final SharedCapabilityClassLoaderFactory sharedCapabilityClassLoaderFactory;

	@Inject
	public ProviderLibraryInstaller(
			@Named("providersPath") @NotNull Path providersPath,
			@Named("capabilitiesPath") @NotNull Path capabilitiesPath,
			@NotNull ProviderLibraryLoggingAdapter loggingHelper,
			@NotNull SharedCapabilityClassLoaderFactory sharedCapabilityClassLoaderFactory
	) {
		this.providersPath = providersPath;
		this.capabilitiesPath = capabilitiesPath;
		this.loggingHelper = loggingHelper;
		this.sharedCapabilityClassLoaderFactory = sharedCapabilityClassLoaderFactory;
	}

	public void installSharedCapabilityApis(@NotNull ProviderLibraries sharedCapabilityApis) {
		install(
				capabilitiesPath,
				".libraries/shared",
				sharedCapabilityApis,
				sharedCapabilityClassLoaderFactory.sharedClassLoader()
		);
	}

	public void installProviderRuntime(
			@NotNull ProviderDescriptor descriptor,
			@NotNull ProviderLibraries providerRuntimeLibraries,
			@NotNull URLClassLoader classLoader
	) {
		install(
				providersPath,
				".libraries/" + descriptor.getId(),
				providerRuntimeLibraries,
				classLoader
		);
	}

	protected void install(
			@NotNull Path basePath,
			@NotNull String cacheNamespace,
			@Nullable ProviderLibraries libraries,
			@NotNull URLClassLoader classLoader
	) {
		if (libraries == null) return;

		var requests = libraries.toLibraryRequests();
		if (requests.isEmpty()) return;

		StandaloneLibraryManager libraryManager = new StandaloneLibraryManager(
				loggingHelper,
				basePath,
				cacheNamespace,
				classLoader
		);
		libraryManager.setVerbosityMode(VerbosityMode.QUIET);
		libraryManager.addMavenLocal();
		libraryManager.addMavenCentral();
		libraryManager.addRepository("https://maven.whereareiam.me/release");
		libraryManager.addRepository("https://maven.whereareiam.me/development");

		if (libraries.getRepositories() != null) {
			for (String repository : libraries.getRepositories())
				libraryManager.addRepository(repository);
		}

		libraryManager.loadLibraries(requests);
	}
}
