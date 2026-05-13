package me.whereareiam.identica.provider;

import com.google.inject.Module;
import lombok.Setter;
import me.whereareiam.identica.conflict.ConflictType;
import me.whereareiam.identica.conflict.resolver.ConflictResolver;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.dependency.ProviderLibraries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;

/**
 * Base class for Identica providers loaded at runtime.
 */
@Setter
@SuppressWarnings("unused")
public abstract class IdenticaProvider {
	protected @NotNull ProviderDescriptor descriptor;
	protected @NotNull Path workingPath;
	protected @Nullable ProviderPlatformExtension platformExtension;

	/**
	 * Provides dependency metadata for this eligibility.
	 *
	 * @return provider libraries descriptor
	 */
	public @NotNull ProviderLibraries libraries() {
		return ProviderLibraries.empty();
	}

	/**
	 * Provides Guice modules for this eligibility.
	 *
	 * @return list of modules to install
	 */
	public @NotNull List<Module> modules() {
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
	 * Provide conflict resolvers owned by this eligibility.
	 *
	 * @return list of eligibility-defined resolvers
	 */
	public @NotNull List<ConflictResolver> getConflictResolvers() {
		return List.of();
	}

	/**
	 * Provide conflict types owned by this eligibility.
	 *
	 * @return list of eligibility-defined conflict types
	 */
	public @NotNull List<ConflictType> getConflictTypes() {
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
