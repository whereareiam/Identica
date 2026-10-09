package me.whereareiam.identica.feature.verification;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.feature.IdenticaFeature;
import me.whereareiam.identica.database.schema.SchemaBootstrap;
import me.whereareiam.identica.feature.verification.database.DefaultVerificationPersistenceService;
import me.whereareiam.identica.feature.verification.challenge.VerificationChallengeStore;
import me.whereareiam.identica.feature.verification.command.VerificationCommandRegistrar;
import me.whereareiam.identica.feature.verification.config.provider.VerificationCommandsProvider;
import me.whereareiam.identica.feature.verification.config.provider.VerificationMessagesProvider;
import me.whereareiam.identica.feature.verification.config.provider.VerificationProvidersProvider;
import me.whereareiam.identica.feature.verification.config.provider.VerificationSettingsProvider;
import me.whereareiam.identica.feature.verification.database.VerificationSchemaContributor;
import me.whereareiam.identica.feature.verification.enrollment.VerificationEnrollmentStore;
import me.whereareiam.identica.feature.FeatureContext;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/** Compiled verification runtime with explicit startup and reverse-order cleanup. */
public final class VerificationFeatureBootstrap implements IdenticaFeature {
	private final Deque<Runnable> shutdownActions = new ArrayDeque<>();

	/** {@inheritDoc} */
	@Override
	public boolean enabledByDefault(@NotNull FeatureContext context) {
		return context.requireInjector().getInstance(me.whereareiam.identica.feature.verification.model.config.VerificationSettings.class).getDefaults().getEnabled();
	}

	@Override
	public @NotNull String id() {
		return "verification";
	}

	/** {@inheritDoc} */
	@Override
	public @NotNull List<Module> modules(@NotNull FeatureContext context) {
		return List.of(new VerificationFeatureConfiguration(context.getFeaturesPath()));
	}

	/** {@inheritDoc} */
	@Override
	public void initialize(@NotNull FeatureContext context) {
		if (!shutdownActions.isEmpty())
			throw new IllegalStateException("Verification is already initialized");
		Injector injector = context.requireInjector();
		injector.getInstance(SchemaBootstrap.class).apply(new VerificationSchemaContributor());
		loadConfig(injector, VerificationSettingsProvider.class);
		loadConfig(injector, VerificationMessagesProvider.class);
		loadConfig(injector, VerificationCommandsProvider.class);
		loadConfig(injector, VerificationProvidersProvider.class);
		VerificationRegistry registry = injector.getInstance(VerificationRegistry.class);
		shutdownActions.push(() -> List.copyOf(registry.values()).forEach(registry::unregister));
		VerificationEnrollmentStore enrollments = injector.getInstance(VerificationEnrollmentStore.class);
		shutdownActions.push(enrollments::clearAll);
		VerificationChallengeStore challenges = injector.getInstance(VerificationChallengeStore.class);
		shutdownActions.push(challenges::clearAll);
		VerificationSessionLifecycle listener = injector.getInstance(VerificationSessionLifecycle.class);
		EventManager events = injector.getInstance(EventManager.class);
		DefaultVerificationPersistenceService persistence = injector.getInstance(DefaultVerificationPersistenceService.class);
		shutdownActions.push(() -> events.unregister(persistence));
		events.register(persistence);
		shutdownActions.push(() -> events.unregister(listener));
		events.register(listener);
		VerificationCommandRegistrar commands = injector.getInstance(VerificationCommandRegistrar.class);
		shutdownActions.push(commands::unregisterCommands);
		commands.registerCommands();
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
