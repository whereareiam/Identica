package me.whereareiam.identica.feature;

import lombok.Builder;
import lombok.Getter;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * Immutable context references for feature contributions to a provider injector.
 * The descriptor is owned by the provider runtime and must be treated as read-only.
 */
@Getter
@Builder
public final class FeatureProviderContext {
	/**
	 * Stable provider identifier.
	 *
	 * @return provider ID
	 */
	private final @NotNull String providerId;
	/**
	 * Directory containing this provider's configuration and data.
	 *
	 * @return provider working directory
	 */
	private final @NotNull Path workingPath;
	/**
	 * Provider metadata, including its declared feature IDs.
	 *
	 * @return provider descriptor
	 */
	private final @NotNull ProviderDescriptor descriptor;
	/**
	 * Registry of compiled features available to this provider.
	 *
	 * @return compiled feature registry
	 */
	private final @NotNull FeatureRegistry features;
}
