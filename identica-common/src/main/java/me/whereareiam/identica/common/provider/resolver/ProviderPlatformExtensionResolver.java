package me.whereareiam.identica.common.provider.resolver;

import com.google.inject.Singleton;
import me.whereareiam.identica.provider.IdenticaProvider;
import me.whereareiam.identica.provider.ProviderPlatformExtension;
import me.whereareiam.identica.type.PlatformType;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

@Singleton
public class ProviderPlatformExtensionResolver {
	private final PlatformType platformType = PlatformType.getType();

	public @Nullable Class<? extends ProviderPlatformExtension> resolve(@Nullable IdenticaProvider provider) {
		if (provider == null) return null;

		return provider.platformExtensions().stream()
				.filter(Objects::nonNull)
				.filter(this::platformMatches)
				.findFirst()
				.orElse(null);
	}

	private boolean platformMatches(
			Class<? extends ProviderPlatformExtension> extensionClass
	) {
		try {
			ProviderPlatformExtension extension = extensionClass.getDeclaredConstructor().newInstance();
			return extension.platform() == platformType;
		} catch (Exception ignored) {
			return false;
		}
	}
}
