package me.whereareiam.identica.feature;

import org.jetbrains.annotations.NotNull;

/**
 * Provider-local integration contributed by an compiled feature. Domain-specific
 * feature contracts extend this interface with the behavior they consume.
 */
public interface ProviderFeatureContribution {
	/**
	 * Identifies the compiled feature owning this contribution.
	 *
	 * @return normalized feature identifier
	 */
	@NotNull String featureId();
}
