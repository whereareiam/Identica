package me.whereareiam.identica.common.provider.runtime.classloader;

import com.google.inject.Singleton;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.net.URLClassLoader;

@Singleton
public class SharedCapabilityClassLoaderFactory {
	private final URLClassLoader classLoader = new URLClassLoader(
			new URL[0],
			getClass().getClassLoader()
	);

	public @NotNull URLClassLoader sharedClassLoader() {
		return classLoader;
	}
}
