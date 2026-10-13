package me.whereareiam.identica.provider;

import com.google.inject.Module;
import lombok.Setter;
import me.whereareiam.identica.conflict.ConflictOutcome;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.dependency.ProviderLibraries;
import me.whereareiam.identica.type.provider.ProviderGuarantee;
import me.whereareiam.identica.feature.FeatureRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Base class for Identica providers loaded at runtime. Implementations must expose
 * a no-argument constructor so dependencies can be checked before injection.
 * Dependency declarations must not depend on injected services.
 */
@Setter
@SuppressWarnings("unused")
public abstract class IdenticaProvider {
	protected @NotNull ProviderDescriptor descriptor;
	protected @NotNull Path workingPath;
	protected @Nullable ProviderPlatformExtension platformExtension;

	/**
	 * Supplies provider-owned runtime libraries. Built-in feature implementations and APIs are supplied by Identica.
	 *
	 * @return provider library descriptor
	 */
	public @NotNull ProviderLibraries libraries() {
		return ProviderLibraries.empty();
	}

	/**
	 * Provides Guice modules for this provider.
	 *
	 * @return list of modules to install
	 */
	public @NotNull List<Module> modules() {
		return List.of();
	}

	/**
	 * Declares what this provider guarantees about its identities. Guarantees are fixed by
	 * the implementation and cannot be enabled through feature configuration.
	 *
	 * @return provider guarantees
	 */
	public @NotNull Set<ProviderGuarantee> guarantees() {
		return Set.of();
	}

	/**
	 * Identifies built-in features this provider integrates with. Operators choose
	 * which supported features to use through provider configuration.
	 *
	 * @return supported feature identifiers
	 */
	public @NotNull Set<String> supportedFeatures() {
		return Set.of();
	}

	/**
	 * Supplies provider-owned integration bindings for compiled features.
	 * Called after supported features have been validated.
	 *
	 * @param features compiled feature registry
	 * @return modules containing available integrations
	 */
	public @NotNull List<Module> featureModules(@NotNull FeatureRegistry features) {
		return List.of();
	}

	/**
	 * Provides platform-specific extensions that can be selected by the runtime
	 * loader for the active platform.
	 *
	 * @return list of available platform extension classes
	 */
	public @NotNull List<Class<? extends ProviderPlatformExtension>> platformExtensions() {
		return List.of();
	}

	/**
	 * Provides conflict outcomes owned by this provider, which conflict rules can name while the provider is loaded.
	 *
	 * @return the provider's conflict outcomes
	 */
	public @NotNull List<ConflictOutcome<?, ?, ?>> conflictOutcomes() {
		return List.of();
	}

	/**
	 * Called after the provider is loaded.
	 */
	public void onLoad() {

	}

	/**
	 * Called when the provider is enabled.
	 */
	public void onEnable() {

	}

	/**
	 * Called when the provider is disabled.
	 */
	public void onDisable() {

	}

	/**
	 * Called when the provider is unloaded.
	 */
	public void onUnload() {

	}
}
