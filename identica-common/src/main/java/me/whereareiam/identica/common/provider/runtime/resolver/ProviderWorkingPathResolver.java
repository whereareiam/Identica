package me.whereareiam.identica.common.provider.runtime.resolver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.identica.model.provider.ProviderDescriptor;

import java.nio.file.Files;
import java.nio.file.Path;

@Singleton
public class ProviderWorkingPathResolver {
	private final Path providersPath;

	@Inject
	public ProviderWorkingPathResolver(@Named("providersPath") Path providersPath) {
		this.providersPath = providersPath;
	}

	public Path resolve(ProviderDescriptor descriptor) {
		return ensureWorkingPath(resolveWorkingDirectoryName(descriptor));
	}

	private Path ensureWorkingPath(String directoryName) {
		Path path = providersPath.resolve(directoryName);
		try {
			Files.createDirectories(path);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to create provider working path: " + path, e);
		}

		return path;
	}

	private String resolveWorkingDirectoryName(ProviderDescriptor descriptor) {
		String name = descriptor != null ? descriptor.getName() : null;
		if (name == null || name.isBlank())
			name = descriptor != null ? descriptor.getId() : null;

		if (name == null || name.isBlank())
			return "unknown";

		return name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
	}
}
