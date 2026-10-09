package me.whereareiam.identica.common.feature;

import com.google.inject.Injector;
import com.google.inject.Provider;
import me.whereareiam.identica.model.config.provider.Providers;
import com.google.inject.Module;
import com.google.inject.name.Names;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.feature.FeatureContext;
import me.whereareiam.identica.feature.FeatureProviderContext;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.IdenticaFeature;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Composes compiled features before root injector creation, then owns their
 * ordered lifecycle. Runtime composition and lifecycle calls belong to the
 * application's startup/shutdown thread.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class FeatureRuntime implements FeatureRegistry, AutoCloseable {
	private final @NotNull Path featuresPath;
	private @Nullable Provider<Providers> providerSettings;
	private final Map<String, Set<String>> supportedByProvider = new java.util.concurrent.ConcurrentHashMap<>();
	private final @NotNull List<FeatureEntry> ordered;
	private final @NotNull Set<String> ids;
	private final @NotNull List<IdenticaFeature> started = new ArrayList<>();
	private @Nullable List<Module> modules;
	private @Nullable FeatureContext lifecycleContext;
	private @NotNull State state = State.PREPARED;

	/**
	 * Composes the feature implementations supplied by Identica's build.
	 *
	 * @param dataPath application data directory
	 * @param features compiled feature implementations
	 * @return runtime with dependency-ordered feature lifecycles
	 */
	public static @NotNull FeatureRuntime prepare(@NotNull Path dataPath, @NotNull Iterable<IdenticaFeature> features) {
		Map<String, FeatureEntry> compiled = index(features);
		List<FeatureEntry> ordered = order(compiled, compiled.keySet());
		Set<String> ids = new LinkedHashSet<>();
		for (FeatureEntry entry : ordered) ids.add(entry.id());
		return new FeatureRuntime(dataPath.resolve("features"), List.copyOf(ordered), Collections.unmodifiableSet(ids));
	}

	/**
	 * Composes compiled features into the root injector and binds this runtime,
	 * its registry, and the parent for provider library loading.
	 * Modules are collected once; contexts do not yet contain an injector. If
	 * composition fails, this runtime cannot be reused.
	 *
	 * @return immutable ordered root modules
	 */
	public @NotNull List<Module> modules() {
		requireOpen();
		if (modules != null) return modules;

		try {
			FeatureContext context = context(null);
			List<Module> composed = new ArrayList<>();
			composed.add(binder -> {
				binder.bind(FeatureRegistry.class).toInstance(this);
				binder.bind(FeatureRuntime.class).toInstance(this);
				binder.bind(ClassLoader.class).annotatedWith(Names.named("providerLibraryParent")).toInstance(getClass().getClassLoader());
			});
			for (FeatureEntry entry : ordered)
				composed.addAll(entry.feature().modules(context));
			modules = List.copyOf(composed);
			return modules;
		} catch (RuntimeException | Error failure) {
			cleanup(failure);
			throw failure;
		}
	}

	/**
	 * Initializes compiled features after the root's bound configurations and
	 * database have been initialized. Startup is attempted once. On failure, the
	 * partially initialized feature and all previously started features are stopped
	 * in reverse order, with cleanup failures suppressed on the original failure.
	 *
	 * @param injector application's root injector containing {@link #modules()}
	 */
	public void initialize(@NotNull Injector injector) {
		if (state != State.PREPARED)
			throw new IllegalStateException("Feature runtime cannot initialize in state " + state);
		if (modules == null)
			throw new IllegalStateException("Feature modules must be composed before initialization");

		providerSettings = injector.getProvider(Providers.class);
		state = State.INITIALIZING;
		lifecycleContext = context(injector);
		try {
			for (FeatureEntry entry : ordered) {
				started.add(entry.feature());
				entry.feature().initialize(lifecycleContext);
			}
			state = State.INITIALIZED;
		} catch (RuntimeException | Error failure) {
			cleanup(failure);
			throw failure;
		}
	}

	/**
	 * Stops features in reverse initialization order.
	 * Every callback is attempted even after a failure. Repeated shutdown is a no-op.
	 */
	public void shutdown() {
		if (state == State.CLOSED) return;
		if (state == State.INITIALIZING)
			throw new IllegalStateException("Cannot shut down features during initialization");
		Throwable failure = cleanup(null);
		if (failure instanceof RuntimeException exception) throw exception;
		if (failure instanceof Error error) throw error;
	}

	/**
	 * Shuts down this runtime, including runtimes closed before initialization.
	 */
	@Override
	public void close() {
		shutdown();
	}

	@Override
	public boolean isAvailable(@NotNull String id) {
		return ids.contains(id.trim().toLowerCase(Locale.ROOT));
	}

	@Override
	public boolean isEnabled(@NotNull String providerId, @NotNull String featureId) {
		String id = normalize(featureId);
		if (!supportedByProvider.getOrDefault(normalize(providerId), Set.of()).contains(id)) return false;
		FeatureEntry entry = ordered.stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
		if (entry == null || lifecycleContext == null) return false;
		for (String dependency : entry.required())
			if (!isEnabled(providerId, dependency)) return false;
		Boolean override = configuredSwitch(providerId, entry.feature().configurationPath());
		return override != null ? override : entry.feature().enabledByDefault(lifecycleContext);
	}

	private @Nullable Boolean configuredSwitch(@NotNull String providerId, @NotNull String path) {
		if (providerSettings == null) return null;
		for (Providers.ProviderEntry provider : providerSettings.get().getProviders()) {
			if (!provider.getId().equalsIgnoreCase(providerId)) continue;
			return provider.getFeatures() == null ? null : provider.getFeatures().enabledOverride(path);
		}
		return null;
	}

	@Override
	public @NotNull Set<String> ids() {
		return ids;
	}

	@Override
	public @NotNull List<Module> providerModules(@NotNull FeatureProviderContext context) {
		requireOpen();
		ProviderDescriptor descriptor = context.getDescriptor();
		Set<String> supported = new TreeSet<>();
		for (String id : descriptor.getSupportedFeatureIds()) supported.add(normalize(id));
		for (FeatureEntry entry : ordered)
			if (Boolean.TRUE.equals(configuredSwitch(context.getProviderId(), entry.feature().configurationPath()))
					&& !supported.contains(entry.id()))
				throw new IllegalStateException("Provider " + context.getProviderId() + " does not support feature " + entry.id());
		supportedByProvider.put(normalize(context.getProviderId()), Set.copyOf(supported));
		List<Module> contributed = new ArrayList<>();
		for (FeatureEntry entry : ordered)
			if (context.getDescriptor().supportsFeature(entry.id()))
				contributed.addAll(entry.feature().providerModules(context));
		return List.copyOf(contributed);
	}

	/**
	 * Creates a provider context and collects modules from its supported features.
	 *
	 * @param providerId provider identifier
	 * @param workingPath provider configuration and data directory
	 * @param descriptor descriptor advertising participating feature IDs
	 * @return immutable provider-local modules in feature dependency order
	 */
	public @NotNull List<Module> providerModules(
			@NotNull String providerId,
			@NotNull Path workingPath,
			@NotNull ProviderDescriptor descriptor
	) {
		return providerModules(FeatureProviderContext.builder()
				.providerId(providerId)
				.workingPath(workingPath)
				.descriptor(descriptor)
				.features(this)
				.build());
	}

	private @NotNull FeatureContext context(@Nullable Injector injector) {
		return FeatureContext.builder()
				.featuresPath(featuresPath)
				.features(this)
				.injector(injector)
				.build();
	}

	private void requireOpen() {
		if (state == State.CLOSED)
			throw new IllegalStateException("Feature runtime is stopped");
	}

	private @Nullable Throwable cleanup(@Nullable Throwable failure) {
		state = State.CLOSED;
		if (lifecycleContext != null)
			for (int i = started.size() - 1; i >= 0; i--)
				try {
					started.get(i).shutdown(lifecycleContext);
				} catch (RuntimeException | Error shutdownFailure) {
					failure = collectFailure(failure, shutdownFailure);
				}
		started.clear();
		supportedByProvider.clear();

		return failure;
	}

	private static @NotNull Throwable collectFailure(@Nullable Throwable failure, @NotNull Throwable next) {
		if (failure == null) return next;
		if (failure != next) failure.addSuppressed(next);
		return failure;
	}

	private static @NotNull Map<String, FeatureEntry> index(@NotNull Iterable<IdenticaFeature> features) {
		Map<String, FeatureEntry> compiled = new TreeMap<>();
		for (IdenticaFeature feature : features) {
			String id = normalize(feature.id());
			FeatureEntry entry = new FeatureEntry(id, feature,
					normalizeDependencies(feature.requiredFeatures()), normalizeDependencies(feature.optionalFeatures()));
			FeatureEntry previous = compiled.putIfAbsent(id, entry);
			if (previous != null)
				throw new IllegalArgumentException("Duplicate feature ID '" + id + "': "
						+ previous.feature().getClass().getName() + " and " + feature.getClass().getName());
		}
		return compiled;
	}

	private static @NotNull List<FeatureEntry> order(
			@NotNull Map<String, FeatureEntry> compiled,
			@NotNull Set<String> compiledIds
	) {
		List<FeatureEntry> ordered = new ArrayList<>();
		Set<String> visited = new LinkedHashSet<>();
		Set<String> visiting = new LinkedHashSet<>();
		for (String id : compiledIds)
			visit(id, compiled, compiledIds, visited, visiting, ordered);
		return ordered;
	}

	private static void visit(
			@NotNull String id,
			@NotNull Map<String, FeatureEntry> compiled,
			@NotNull Set<String> compiledIds,
			@NotNull Set<String> visited,
			@NotNull Set<String> visiting,
			@NotNull List<FeatureEntry> ordered
	) {
		if (visited.contains(id)) return;
		if (!visiting.add(id))
			throw new IllegalArgumentException("Feature dependency cycle: " + String.join(" -> ", visiting) + " -> " + id);
		FeatureEntry entry = compiled.get(id);
		for (String required : entry.required())
			if (!compiledIds.contains(required))
				throw new IllegalArgumentException("Feature '" + id + "' requires feature '" + required + "' to be compiled into Identica");
		Set<String> dependencies = new TreeSet<>(entry.required());
		for (String optional : entry.optional())
			if (compiledIds.contains(optional)) dependencies.add(optional);
		for (String dependency : dependencies)
			visit(dependency, compiled, compiledIds, visited, visiting, ordered);
		visiting.remove(id);
		visited.add(id);
		ordered.add(entry);
	}

	private static @NotNull Set<String> normalizeDependencies(@NotNull Set<String> dependencies) {
		Set<String> normalized = new TreeSet<>();
		for (String dependency : dependencies)
			if (!normalized.add(normalize(dependency)))
				throw new IllegalArgumentException("Duplicate feature dependency ID '" + dependency + "'");
		return Collections.unmodifiableSet(normalized);
	}

	private static @NotNull String normalize(@NotNull String id) {
		String normalized = id.trim().toLowerCase(Locale.ROOT);
		if (normalized.isEmpty() || normalized.contains(","))
			throw new IllegalArgumentException("Invalid feature ID '" + id + "'");
		return normalized;
	}

	private enum State {
		PREPARED, INITIALIZING, INITIALIZED, CLOSED
	}

	private record FeatureEntry(
			@NotNull String id,
			@NotNull IdenticaFeature feature,
			@NotNull Set<String> required,
			@NotNull Set<String> optional
	) {
	}
}
