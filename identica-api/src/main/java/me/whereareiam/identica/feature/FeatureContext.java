package me.whereareiam.identica.feature;

import com.google.inject.Injector;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Immutable context for root module composition and feature lifecycle callbacks.
 * A feature may resolve its own storage directory beneath the shared features path.
 *
 * <pre>{@code
 * Path workingPath = context.getFeaturesPath().resolve("verification");
 * }</pre>
 */
@Getter
@Builder
public final class FeatureContext {
	/**
	 * Shared directory containing feature configuration and data.
	 *
	 * @return shared features directory
	 */
	private final @NotNull Path featuresPath;
	/**
	 * Registry of compiled features and provider configuration.
	 *
	 * @return feature registry
	 */
	private final @NotNull FeatureRegistry features;
	/**
	 * Root injector; null only while composing modules before injector creation.
	 *
	 * @return root injector, or null during module composition
	 */
	private final @Nullable Injector injector;

	/**
	 * Obtains the root injector during a lifecycle callback.
	 *
	 * @return initialized root injector
	 * @throws IllegalStateException when called during module composition
	 */
	public @NotNull Injector requireInjector() {
		if (injector == null)
			throw new IllegalStateException("The root injector is unavailable during feature module composition");
		return injector;
	}
}
