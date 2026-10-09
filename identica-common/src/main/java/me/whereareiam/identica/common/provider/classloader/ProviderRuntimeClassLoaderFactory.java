package me.whereareiam.identica.common.provider.classloader;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.logging.Logger;
import org.jetbrains.annotations.NotNull;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

@Singleton
public class ProviderRuntimeClassLoaderFactory {
	private final ClassLoader parentClassLoader;

	@Inject
	public ProviderRuntimeClassLoaderFactory(@NotNull SharedLibraryClassLoaderFactory sharedLibraryClassLoaderFactory) {
		this.parentClassLoader = sharedLibraryClassLoaderFactory.sharedClassLoader();
	}

	public URLClassLoader create(Path jarPath) throws MalformedURLException {
		return new URLClassLoader(
				new URL[]{jarPath.toUri().toURL()},
				parentClassLoader
		);
	}

	public void close(Object classLoader) {
		if (classLoader instanceof AutoCloseable closeable) {
			try {
				closeable.close();
			} catch (Exception e) {
				Logger.warn("Failed to close provider runtime classloader: %s", e.getMessage());
			}
		}
	}
}
