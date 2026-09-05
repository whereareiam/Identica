package me.whereareiam.identica.model.provider;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.model.provider.dependency.ProviderLibraries;
import me.whereareiam.identica.type.provider.ProviderTrait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Set;
import java.util.List;
import java.util.Locale;

/**
 * Descriptor for a provider module.
 */
@Getter
@Setter
@ToString
@SuppressWarnings("unused")
public class ProviderDescriptor {
	private @NotNull String id;
	private @NotNull String name;
	private @NotNull String version;

	private @NotNull String main;
	private @Nullable List<String> authors;
	private @NotNull List<String> supportedPlatforms;

	/**
	 * Identity guarantees declared by the provider implementation.
	 */
	private @NotNull Set<ProviderTrait> traits = Set.of();
	/**
	 * Feature integrations supported by the provider, independently of enablement.
	 */
	private @NotNull List<String> supportedFeatureIds = new ArrayList<>();

	private int priority = 0;

	private ProviderLibraries libraries;

	/**
	 * Checks a guarantee supplied by this provider implementation.
	 *
	 * @param trait identity trait to check
	 * @return whether the provider supplies the guarantee
	 */
	public boolean hasTrait(@NotNull ProviderTrait trait) {
		return traits.contains(trait);
	}

	/**
	 * Checks whether the provider advertises a feature id.
	 *
	 * @param featureId feature id to check
	 * @return {@code true} when the id is listed
	 */
	public boolean supportsFeature(@Nullable String featureId) {
		if (featureId == null || featureId.isBlank() || supportedFeatureIds.isEmpty()) return false;

		String normalized = featureId.trim().toLowerCase(Locale.ROOT);
		for (String entry : supportedFeatureIds) {
			if (entry == null || entry.isBlank()) continue;
			if (normalized.equals(entry.trim().toLowerCase(Locale.ROOT))) return true;
		}

		return false;
	}
}
