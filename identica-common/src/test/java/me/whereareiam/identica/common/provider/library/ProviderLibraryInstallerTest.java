package me.whereareiam.identica.common.provider.library;

import me.whereareiam.identica.common.provider.classloader.SharedLibraryClassLoaderFactory;
import me.whereareiam.identica.common.provider.dependency.ProviderDependencyLoggingAdapter;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.dependency.ProviderLibraries;
import me.whereareiam.identica.model.provider.dependency.ProviderLibrary;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class ProviderLibraryInstallerTest {
	@Test
	void installsSharedLibrariesIntoSharedLoaderAndProviderRuntimeIntoProviderLoader(@TempDir Path tempDir) {
		SharedLibraryClassLoaderFactory sharedLibraryClassLoaderFactory = new SharedLibraryClassLoaderFactory(getClass().getClassLoader());
		RecordingProviderLibraryInstaller installer = new RecordingProviderLibraryInstaller(
				tempDir,
				mock(ProviderDependencyLoggingAdapter.class),
				sharedLibraryClassLoaderFactory
		);
		URLClassLoader providerClassLoader = new URLClassLoader(new java.net.URL[0], sharedLibraryClassLoaderFactory.sharedClassLoader());

		installer.installSharedLibraries(libraries(shared("recognition-api"), shared("external-api")));
		Call sharedCall = installer.lastCall();
		installer.installProviderRuntime(
				descriptor(),
				libraries(local("recognition-runtime"), local("external-runtime")),
				providerClassLoader
		);
		Call providerCall = installer.lastCall();

		assertEquals(tempDir, sharedCall.basePath());
		assertEquals(".libraries/shared", sharedCall.cacheNamespace());
		assertSame(sharedLibraryClassLoaderFactory.sharedClassLoader(), sharedCall.classLoader());
		assertEquals(Set.of("recognition-api", "external-api"), artifactIds(sharedCall.libraries()));

		assertEquals(tempDir, providerCall.basePath());
		assertEquals(".libraries/test-provider", providerCall.cacheNamespace());
		assertSame(providerClassLoader, providerCall.classLoader());
		assertEquals(Set.of("recognition-runtime", "external-runtime"), artifactIds(providerCall.libraries()));
	}

	private static @NotNull ProviderDescriptor descriptor() {
		ProviderDescriptor descriptor = new ProviderDescriptor();
		descriptor.setId("test-provider");
		descriptor.setName("Test Provider");
		descriptor.setVersion("1.0.0");
		descriptor.setMain("example.Provider");
		descriptor.setSupportedPlatforms(List.of("ANY"));
		return descriptor;
	}

	private static @NotNull ProviderLibraries libraries(@NotNull ProviderLibrary... libraries) {
		ProviderLibraries providerLibraries = new ProviderLibraries();
		providerLibraries.setLibraries(List.of(libraries));
		return providerLibraries;
	}

	private static @NotNull Set<String> artifactIds(@NotNull ProviderLibraries libraries) {
		return libraries.getLibraries().stream()
				.map(ProviderLibrary::getArtifactId)
				.collect(java.util.stream.Collectors.toSet());
	}

	private static @NotNull ProviderLibrary shared(@NotNull String artifactId) {
		return ProviderLibrary.builder()
				.groupId("example.provider.library")
				.artifactId(artifactId)
				.version("1.0.0")
				.loader("shared")
				.build();
	}

	private static @NotNull ProviderLibrary local(@NotNull String artifactId) {
		return ProviderLibrary.builder()
				.groupId("example.provider.library")
				.artifactId(artifactId)
				.version("1.0.0")
				.build();
	}

	private static final class RecordingProviderLibraryInstaller extends ProviderLibraryInstaller {
		private Call lastCall;

		private RecordingProviderLibraryInstaller(
				@NotNull Path providersPath,
				@NotNull ProviderDependencyLoggingAdapter loggingHelper,
				@NotNull SharedLibraryClassLoaderFactory sharedLibraryClassLoaderFactory
		) {
			super(providersPath, loggingHelper, sharedLibraryClassLoaderFactory);
		}

		@Override
		protected void install(
				@NotNull Path basePath,
				@NotNull String cacheNamespace,
				@NotNull ProviderLibraries libraries,
				@NotNull URLClassLoader classLoader
		) {
			lastCall = new Call(basePath, cacheNamespace, libraries, classLoader);
		}

		private @NotNull Call lastCall() {
			return lastCall;
		}
	}

	private record Call(
			@NotNull Path basePath,
			@NotNull String cacheNamespace,
			@NotNull ProviderLibraries libraries,
			@NotNull URLClassLoader classLoader
	) {
	}
}
