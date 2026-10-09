package me.whereareiam.identica.common.provider.library;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.attache.platform.standalone.StandaloneLibraryManager;
import me.whereareiam.attache.type.VerbosityMode;
import me.whereareiam.identica.common.provider.classloader.SharedLibraryClassLoaderFactory;
import me.whereareiam.identica.common.provider.dependency.ProviderDependencyLoggingAdapter;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.dependency.ProviderLibraries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URLClassLoader;
import java.nio.file.Path;

@Singleton
public class ProviderLibraryInstaller {
	private final Path providersPath;
	private final ProviderDependencyLoggingAdapter loggingHelper;
	private final SharedLibraryClassLoaderFactory sharedLibraryClassLoaderFactory;

	@Inject
	public ProviderLibraryInstaller(
			@Named("providersPath") @NotNull Path providersPath,
			@NotNull ProviderDependencyLoggingAdapter loggingHelper,
			@NotNull SharedLibraryClassLoaderFactory sharedLibraryClassLoaderFactory
	) {
		this.providersPath = providersPath;
		this.loggingHelper = loggingHelper;
		this.sharedLibraryClassLoaderFactory = sharedLibraryClassLoaderFactory;
	}

	public void installSharedLibraries(@NotNull ProviderLibraries sharedLibraries) {
		install(
				providersPath,
				".libraries/shared",
				sharedLibraries,
				sharedLibraryClassLoaderFactory.sharedClassLoader()
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
		libraryManager.addRepository("https://registry.whereareiam.me/maven/packages");

		if (libraries.getRepositories() != null) {
			for (String repository : libraries.getRepositories())
				libraryManager.addRepository(repository);
		}

		libraryManager.loadLibraries(requests);
	}
}
