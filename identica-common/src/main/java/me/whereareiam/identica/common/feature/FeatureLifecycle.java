package me.whereareiam.identica.common.feature;

import com.google.inject.Inject;
import com.google.inject.Injector;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import org.jetbrains.annotations.NotNull;

/**
 * Starts the compiled features of the {@link FeatureRuntime} once the core subsystems run, and stops them in
 * reverse when Identica stops.
 */
public final class FeatureLifecycle implements RuntimeLifecycle {
	private final FeatureRuntime features;
	private final Injector injector;

	@Inject
	public FeatureLifecycle(@NotNull FeatureRuntime features, @NotNull Injector injector) {
		this.features = features;
		this.injector = injector;
	}

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.FEATURES;
	}

	@Override
	public void initialize() {
		features.initialize(injector);
	}

	@Override
	public void shutdown() {
		features.shutdown();
	}
}
