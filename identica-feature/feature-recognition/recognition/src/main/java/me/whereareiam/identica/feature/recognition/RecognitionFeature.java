package me.whereareiam.identica.feature.recognition;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.feature.IdenticaFeature;
import me.whereareiam.identica.feature.FeatureContext;
import me.whereareiam.identica.feature.FeatureProviderContext;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionSettingsProvider;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionProvidersProvider;
import me.whereareiam.identica.feature.recognition.eligibility.RecognitionEligibilityRegistry;
import me.whereareiam.identica.feature.recognition.eligibility.RecognitionEligibilityRule;
import me.whereareiam.identica.feature.recognition.eligibility.rule.ExplicitSelectionRecognitionEligibilityRule;
import me.whereareiam.identica.feature.recognition.eligibility.rule.RecognitionEnabledEligibilityRule;
import me.whereareiam.identica.feature.recognition.eligibility.rule.UntrustedIpRecognitionEligibilityRule;
import me.whereareiam.identica.feature.recognition.pipeline.RecognitionAppliedLifecycle;
import me.whereareiam.identica.feature.recognition.pipeline.RecognitionPipelineExtension;
import me.whereareiam.identica.feature.recognition.compatibility.restriction.join.RecognitionJoinIntegration;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Optional recognition runtime with explicitly owned registrations.
 */
public final class RecognitionFeature implements IdenticaFeature {
	private final Deque<Runnable> cleanup = new ArrayDeque<>();

	@Override
	public boolean enabledByDefault(@NotNull FeatureContext context) {
		return context.requireInjector().getInstance(me.whereareiam.identica.feature.recognition.config.RecognitionSettings.class).isEnabled();
	}

	@Override
	public @NotNull String id() {
		return RecognitionFeatureId.ID;
	}

	@Override
	public @NotNull Set<String> optionalFeatures() {
		return Set.of("restriction-join");
	}

	@Override
	public @NotNull List<Module> modules(@NotNull FeatureContext context) {
		return List.of(new RecognitionModule(context.getFeaturesPath().resolve(id())));
	}

	@Override
	public @NotNull List<Module> providerModules(@NotNull FeatureProviderContext context) {
		return context.getFeatures().isAvailable("restriction-join")
				? List.of(new RecognitionProviderModule())
				: List.of();
	}

	@Override
	public void initialize(@NotNull FeatureContext context) {
		Injector injector = context.requireInjector();
		Registry<Reloadable> reloadables = injector.getInstance(Key.get(new TypeLiteral<Registry<Reloadable>>() {}));
		cleanup.addFirst(() -> reloadables.unregister(injector.getInstance(RecognitionSettingsProvider.class)));
		cleanup.addFirst(() -> reloadables.unregister(injector.getInstance(RecognitionProvidersProvider.class)));
		injector.getInstance(RecognitionSettings.class);
		RecognitionEligibilityRegistry eligibility = injector.getInstance(RecognitionEligibilityRegistry.class);
		for (Class<? extends RecognitionEligibilityRule> type : List.of(
				RecognitionEnabledEligibilityRule.class,
				ExplicitSelectionRecognitionEligibilityRule.class,
				UntrustedIpRecognitionEligibilityRule.class
		)) {
			RecognitionEligibilityRule rule = injector.getInstance(type);
			cleanup.addFirst(() -> eligibility.unregister(rule.id()));
			eligibility.register(rule);
		}
		PipelineExtensionRegistry pipelines = injector.getInstance(PipelineExtensionRegistry.class);
		RecognitionPipelineExtension extension = injector.getInstance(RecognitionPipelineExtension.class);
		cleanup.addFirst(() -> pipelines.unregister(extension.id()));
		pipelines.register(extension);
		EventManager events = injector.getInstance(EventManager.class);
		RecognitionAppliedLifecycle listener = injector.getInstance(RecognitionAppliedLifecycle.class);
		cleanup.addFirst(() -> events.unregister(listener));
		events.register(listener);
		if (context.getFeatures().isAvailable("restriction-join"))
			cleanup.addFirst(RecognitionJoinIntegration.register(injector));
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
