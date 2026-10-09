package me.whereareiam.identica.feature;

import com.google.inject.Module;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

/**
 * A feature compiled into Identica, with shared services and provider integrations.
 * Constructors and module composition must not acquire lifecycle resources.
 */
public interface IdenticaFeature {
	/**
	 * Returns the stable feature identifier, shared by configuration and dependencies.
	 * IDs are compared after trimming and conversion to lower case.
	 *
	 * @return nonblank feature identifier
	 */
	@NotNull String id();

	/**
	 * Reads the shared default for this feature's provider enable switch.
	 *
	 * @param context initialized runtime context
	 * @return the default applied when a provider omits its enable switch
	 */
	default boolean enabledByDefault(@NotNull FeatureContext context) {
		return true;
	}

	/**
	 * Returns the provider settings path beneath the features object.
	 *
	 * @return dot-separated settings path; defaults to the feature identifier
	 */
	default @NotNull String configurationPath() {
		return id();
	}

	/**
	 * Declares features that must also be compiled and initialized first.
	 *
	 * @return required feature IDs; empty when there are no dependencies
	 */
	default @NotNull Set<String> requiredFeatures() {
		return Set.of();
	}

	/**
	 * Declares features to initialize first when available. This describes internal initialization order, not operator selection.
	 *
	 * @return optional feature IDs
	 */
	default @NotNull Set<String> optionalFeatures() {
		return Set.of();
	}

	/**
	 * Contributes modules to the application's root injector before it is created.
	 * Feature bindings and multibindings participate directly in root composition.
	 *
	 * @param context feature context with a null injector
	 * @return modules to install, in installation order
	 */
	@NotNull List<Module> modules(@NotNull FeatureContext context);

	/**
	 * Starts this feature after root configuration and database initialization.
	 * If this method fails, shutdown is attempted for this partially started feature
	 * and then for previously initialized features in reverse order.
	 *
	 * @param context feature context containing the root injector
	 */
	default void initialize(@NotNull FeatureContext context) {
	}

	/**
	 * Releases resources acquired during initialization, including partial startup.
	 * Dependencies remain available until this callback finishes.
	 *
	 * @param context feature context containing the root injector used at startup
	 */
	default void shutdown(@NotNull FeatureContext context) {
	}

	/**
	 * Contributes provider-local modules when that provider declares this feature.
	 *
	 * @param context provider identity, storage location, descriptor, and features
	 * @return provider-local modules, or an empty list when none are needed
	 */
	default @NotNull List<Module> providerModules(@NotNull FeatureProviderContext context) {
		return List.of();
	}
}
