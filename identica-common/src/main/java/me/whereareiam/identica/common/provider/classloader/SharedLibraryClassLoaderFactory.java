package me.whereareiam.identica.common.provider.classloader;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.net.URLClassLoader;

@Singleton
public class SharedLibraryClassLoaderFactory implements AutoCloseable {
	private final URLClassLoader classLoader;

	@Inject
	public SharedLibraryClassLoaderFactory(@Named("providerLibraryParent") @NotNull ClassLoader parent) {
		classLoader = new URLClassLoader(new URL[0], parent);
	}

	public @NotNull URLClassLoader sharedClassLoader() {
		return classLoader;
	}

	@Override
	public void close() throws java.io.IOException {
		classLoader.close();
	}
}
