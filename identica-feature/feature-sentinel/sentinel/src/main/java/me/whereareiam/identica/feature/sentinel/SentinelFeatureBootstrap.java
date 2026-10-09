package me.whereareiam.identica.feature.sentinel;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.feature.IdenticaFeature;
import me.whereareiam.identica.feature.sentinel.config.provider.SentinelMessagesProvider;
import me.whereareiam.identica.feature.sentinel.config.provider.SentinelSettingsProvider;
import me.whereareiam.identica.feature.sentinel.type.ResumeSpamSentinelDefinition;
import me.whereareiam.identica.feature.FeatureContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Compiled sentinel runtime with explicit startup and reverse-order cleanup. */
public final class SentinelFeatureBootstrap implements IdenticaFeature {
	private final Deque<Runnable> shutdownActions = new ArrayDeque<>();

	/** {@inheritDoc} */
	@Override
	public boolean enabledByDefault(@NotNull FeatureContext context) {
		return context.requireInjector().getInstance(me.whereareiam.identica.feature.sentinel.model.config.SentinelSettings.class).isEnabled();
	}

	@Override
	public @NotNull String id() {
		return "sentinel";
	}

	/** {@inheritDoc} */
	@Override
	public @NotNull List<Module> modules(@NotNull FeatureContext context) {
		return List.of(new SentinelFeatureConfiguration(context.getFeaturesPath()));
	}

	/** {@inheritDoc} */
	@Override
	public void initialize(@NotNull FeatureContext context) {
		if (!shutdownActions.isEmpty())
			throw new IllegalStateException("Sentinel is already initialized");
		Injector injector = context.requireInjector();
		loadConfig(injector, SentinelSettingsProvider.class);
		loadConfig(injector, SentinelMessagesProvider.class);
		Registry<SentinelDefinition> registry = injector.getInstance(Key.get(new TypeLiteral<Registry<SentinelDefinition>>() {}));
		shutdownActions.push(() -> List.copyOf(registry.values()).forEach(registry::unregister));
		registry.register(injector.getInstance(ResumeSpamSentinelDefinition.class));
		ConnectionAttemptSentinelLifecycle listener = injector.getInstance(ConnectionAttemptSentinelLifecycle.class);
		EventManager events = injector.getInstance(EventManager.class);
		shutdownActions.push(() -> events.unregister(listener));
		events.register(listener);
	}

	/** {@inheritDoc} */
	@Override
	public void shutdown(@NotNull FeatureContext context) {
		RuntimeException failure = null;
		while (!shutdownActions.isEmpty()) {
			try {
				shutdownActions.pop().run();
			} catch (RuntimeException exception) {
				if (failure == null) failure = exception;
				else failure.addSuppressed(exception);
			}
		}
		if (failure != null) throw failure;
	}

	private void loadConfig(Injector injector, Class<? extends ConfigProvider<?>> type) {
		ConfigProvider<?> provider = injector.getInstance(type);
		Registry<Reloadable> reloadables = injector.getInstance(Key.get(new TypeLiteral<Registry<Reloadable>>() {}));
		shutdownActions.push(() -> reloadables.unregister(provider));
		provider.get();
	}
}
