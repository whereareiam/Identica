package me.whereareiam.identica.common;

import com.google.inject.Inject;
import com.google.inject.Provider;
import me.whereareiam.identica.IdenticaAPI;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import org.jetbrains.annotations.NotNull;

/**
 * Registers Identica's API for other plugins as the first of the core subsystems, and removes it when Identica
 * stops. The API is marked ready later, once Identica handles connections.
 */
public final class ApiLifecycle implements RuntimeLifecycle {
	private final Provider<DefaultIdenticaAPI> api;
	private boolean registered;

	@Inject
	public ApiLifecycle(@NotNull Provider<DefaultIdenticaAPI> api) {
		this.api = api;
	}

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.RUNTIME;
	}

	@Override
	public void initialize() {
		IdenticaAPI.register(api.get());
		registered = true;
	}

	@Override
	public void shutdown() {
		if (!registered) return;

		registered = false;
		IdenticaAPI.unregister();
	}
}
