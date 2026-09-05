package me.whereareiam.identica.feature.restriction.join;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.feature.IdenticaFeature;
import me.whereareiam.identica.feature.FeatureContext;
import me.whereareiam.identica.feature.FeatureProviderContext;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.feature.restriction.RestrictionFeatureId;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionProvidersProvider;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionSettings;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionSettingsProvider;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionCommandsProvider;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionMessagesProvider;
import me.whereareiam.identica.feature.restriction.join.pipeline.JoinPipelineExtension;
import me.whereareiam.identica.feature.restriction.model.RestrictionSignalDescriptor;
import me.whereareiam.identica.feature.restriction.model.RestrictionTypeDescriptor;
import me.whereareiam.identica.feature.restriction.registry.RestrictionSignalRegistry;
import me.whereareiam.identica.feature.restriction.registry.type.RestrictionTypeRegistry;
import me.whereareiam.identica.feature.restriction.registry.type.RestrictionTypeResolverRegistry;
import me.whereareiam.identica.feature.restriction.type.RestrictionSignal;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;

/**
 * Optional join restriction runtime with explicitly owned registrations.
 * The runtime ID is restriction-join; provider overrides use features.restriction.join.
 */
public final class JoinRestrictionFeature implements IdenticaFeature {
	private final Deque<Runnable> cleanup = new ArrayDeque<>();

	@Override
	public boolean enabledByDefault(@NotNull FeatureContext context) {
		return context.requireInjector().getInstance(me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionSettings.class).isEnabled();
	}

	@Override
	public @NotNull String configurationPath() {
		return "restriction.join";
	}

	@Override
	public @NotNull String id() {
		return JoinRestrictionFeatureId.ID;
	}

	@Override
	public @NotNull Set<String> requiredFeatures() {
		return Set.of(RestrictionFeatureId.ID);
	}

	@Override
	public @NotNull List<Module> modules(@NotNull FeatureContext context) {
		return List.of(new JoinRestrictionModule(context.getFeaturesPath().resolve("restriction").resolve("join")));
	}

	@Override
	public @NotNull List<Module> providerModules(@NotNull FeatureProviderContext context) {
		return List.of(new JoinRestrictionProviderModule());
	}

	@Override
	public void initialize(@NotNull FeatureContext context) {
		Injector injector = context.requireInjector();
		Registry<Reloadable> reloadables = injector.getInstance(Key.get(new TypeLiteral<Registry<Reloadable>>() {}));
		cleanup.addFirst(() -> reloadables.unregister(injector.getInstance(JoinRestrictionSettingsProvider.class)));
		cleanup.addFirst(() -> reloadables.unregister(injector.getInstance(JoinRestrictionProvidersProvider.class)));
		cleanup.addFirst(() -> reloadables.unregister(injector.getInstance(JoinRestrictionCommandsProvider.class)));
		cleanup.addFirst(() -> reloadables.unregister(injector.getInstance(JoinRestrictionMessagesProvider.class)));
		injector.getInstance(JoinRestrictionSettings.class);
		RestrictionTypeRegistry types = injector.getInstance(RestrictionTypeRegistry.class);
		RestrictionTypeDescriptor type = RestrictionTypeDescriptor.builder()
				.type(JoinRestrictionType.TYPE)
				.displayName("Join")
				.description("Runtime restriction evaluated during connection preparation and scenario entry.")
				.build();
		cleanup.addFirst(() -> types.unregister(type));
		types.register(type);
		RestrictionSignalRegistry signals = injector.getInstance(RestrictionSignalRegistry.class);
		RestrictionSignalDescriptor linked = RestrictionSignalDescriptor.builder()
				.restrictionType(JoinRestrictionType.TYPE)
				.signal(RestrictionSignal.of("linked"))
				.displayName("Linked")
				.description("Allows already-linked provider subjects through join restriction.")
				.build();
		cleanup.addFirst(() -> signals.unregister(linked));
		signals.register(linked);
		RestrictionTypeResolverRegistry resolvers = injector.getInstance(RestrictionTypeResolverRegistry.class);
		JoinRestrictionTypeResolver resolver = injector.getInstance(JoinRestrictionTypeResolver.class);
		cleanup.addFirst(() -> resolvers.unregister(resolver));
		resolvers.register(resolver);
		CommandRegistrar commands = injector.getInstance(CommandRegistrar.class);
		cleanup.addFirst(commands::unregisterCommands);
		commands.registerCommands();
		PipelineExtensionRegistry pipelines = injector.getInstance(PipelineExtensionRegistry.class);
		JoinPipelineExtension extension = injector.getInstance(JoinPipelineExtension.class);
		cleanup.addFirst(() -> pipelines.unregister(extension.id()));
		pipelines.register(extension);
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
