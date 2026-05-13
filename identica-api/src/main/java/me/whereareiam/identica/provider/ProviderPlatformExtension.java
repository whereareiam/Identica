package me.whereareiam.identica.provider;

import com.google.inject.Module;
import me.whereareiam.identica.type.PlatformType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Platform-specific extension hook for runtime-loaded providers.
 * Implementations may contribute additional Guice modules and lifecycle
 * behavior for a single platform.
 */
public interface ProviderPlatformExtension {
	/**
	 * Returns the platform this extension targets.
	 *
	 * @return supported platform
	 */
	@NotNull PlatformType platform();

	/**
	 * Provides Guice modules that should be installed when this extension is
	 * selected for the active platform.
	 *
	 * @return modules to install
	 */
	default @NotNull List<Module> modules() {
		return List.of();
	}

	/**
	 * Called when the owning provider is enabled.
	 */
	default void onEnable() {
	}

	/**
	 * Called when the owning provider is disabled.
	 */
	default void onDisable() {
	}
}
