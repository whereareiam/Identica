package me.whereareiam.identica.common.provider.discovery;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.provider.ProviderDescriptorReader;
import me.whereareiam.identica.type.provider.ProviderState;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
public class ProviderDiscovery {
	private static final String PROVIDER_FILE = "provider.json";

	private final Path providersPath;
	private final ProviderDescriptorReader descriptorReader;

	@Inject
	public ProviderDiscovery(
			@Named("providersPath") Path providersPath,
			ProviderDescriptorReader descriptorReader
	) {
		this.providersPath = providersPath;
		this.descriptorReader = descriptorReader;
	}

	public List<InternalProvider> discover(Collection<InternalProvider> existingProviders) {
		try (Stream<Path> paths = Files.list(providersPath)) {
			return paths
					.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().endsWith(".jar"))
					.map(path -> readProviderDescriptor(path, existingProviders))
					.filter(Objects::nonNull)
					.collect(Collectors.toList());
		} catch (Exception e) {
			Logger.warn("Failed to scan providers directory: %s", providersPath);
			Logger.debug("Provider scan error: %s", e.getMessage());
			return List.of();
		}
	}

	private InternalProvider readProviderDescriptor(Path jarPath, Collection<InternalProvider> existingProviders) {
		try (JarFile jarFile = new JarFile(jarPath.toFile())) {
			JarEntry entry = jarFile.getJarEntry(PROVIDER_FILE);

			if (entry == null) {
				Logger.warn("Provider file does not contain %s: %s", PROVIDER_FILE, jarPath.getFileName());
				return null;
			}

			try (InputStream stream = jarFile.getInputStream(entry)) {
				ProviderDescriptor descriptor = descriptorReader.read(stream);
				if (!validateDescriptor(descriptor, jarPath, existingProviders))
					return null;

				return InternalProvider.builder()
						.path(jarPath)
						.descriptor(descriptor)
						.state(ProviderState.DISCOVERED)
						.build();
			}
		} catch (Exception e) {
			Logger.warn("Failed to load provider from %s: %s", jarPath.getFileName(), e.getMessage());
			return null;
		}
	}

	private boolean validateDescriptor(
			ProviderDescriptor descriptor,
			Path jarPath,
			Collection<InternalProvider> existingProviders
	) {
		if (descriptor == null) {
			Logger.warn("Failed to read provider descriptor: %s", jarPath.getFileName());
			return false;
		}

		if (descriptor.getId().isBlank() || descriptor.getName().isBlank() || descriptor.getMain().isBlank() || descriptor.getVersion().isBlank()) {
			Logger.warn("Provider descriptor missing required fields: %s", jarPath.getFileName());
			return false;
		}

		if (existingProviders.stream().anyMatch(existing -> existing.getDescriptor().getId().equalsIgnoreCase(descriptor.getId()))) {
			Logger.warn("Provider with id already exists: %s", descriptor.getId());
			return false;
		}

		return true;
	}
}
