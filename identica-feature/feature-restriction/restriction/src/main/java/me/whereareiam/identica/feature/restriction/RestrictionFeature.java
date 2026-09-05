package me.whereareiam.identica.feature.restriction;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.feature.IdenticaFeature;
import me.whereareiam.identica.feature.FeatureContext;
import me.whereareiam.identica.feature.restriction.config.RestrictionSettings;
import me.whereareiam.identica.feature.restriction.config.provider.RestrictionSettingsProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Optional restriction runtime with explicitly owned registrations.
 */
public final class RestrictionFeature implements IdenticaFeature {
	private final Deque<Runnable> cleanup = new ArrayDeque<>();

	@Override
	public boolean enabledByDefault(@NotNull FeatureContext context) {
		return context.requireInjector().getInstance(me.whereareiam.identica.feature.restriction.config.RestrictionSettings.class).isEnabled();
	}

	@Override
	public @NotNull String id() {
		return RestrictionFeatureId.ID;
	}

	@Override
	public @NotNull List<Module> modules(@NotNull FeatureContext context) {
		return List.of(new RestrictionModule(context.getFeaturesPath().resolve(id())));
	}

	@Override
	public void initialize(@NotNull FeatureContext context) {
		Injector injector = context.requireInjector();
		Registry<Reloadable> reloadables = injector.getInstance(Key.get(new TypeLiteral<Registry<Reloadable>>() {}));
		cleanup.addFirst(() -> reloadables.unregister(injector.getInstance(RestrictionSettingsProvider.class)));
		injector.getInstance(RestrictionSettings.class);
	}

	@Override
	public void shutdown(@NotNull FeatureContext context) {
		RuntimeException failure = null;
		while (!cleanup.isEmpty()) {
			try {
				cleanup.removeFirst().run();
			} catch (RuntimeException exception) {
				if (failure == null) failure = exception;
				else failure.addSuppressed(exception);
			}
		}
		if (failure != null) throw failure;
	}
}
