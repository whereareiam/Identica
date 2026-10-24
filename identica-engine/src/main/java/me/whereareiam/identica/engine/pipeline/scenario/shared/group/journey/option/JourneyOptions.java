package me.whereareiam.identica.engine.pipeline.scenario.shared.group.journey.option;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.pipeline.journey.JourneyOverrideItem;
import me.whereareiam.identica.model.pipeline.journey.ProviderMethod;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.registry.JourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.AuthenticationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.MigrationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.RegistrationJourneyRegistry;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.selection.ProviderSelector;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Lists the options of a scenario for a connection, in the order they are tried: the eligible providers ranked by
 * the connection's selection, each with its available methods in the order its provider entry names them, or the
 * provider's own order. A migration offers its target provider only. The method the player picked, or the one a
 * waiting journey runs, comes first within its provider.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JourneyOptions {
	private final ProviderOperations providerOperations;
	private final ProviderSelector providerSelector;
	// Through providers: the registries hold the steps that list the options, such as the provider choice.
	private final Provider<AuthenticationJourneyRegistry> authenticationJourneyRegistry;
	private final Provider<RegistrationJourneyRegistry> registrationJourneyRegistry;
	private final Provider<MigrationJourneyRegistry> migrationJourneyRegistry;
	private final Provider<Providers> providersProvider;

	/**
	 * Lists the options of a scenario.
	 *
	 * @param context scenario context of the connection
	 * @param pipelineType scenario
	 * @param override excluded providers and methods of the journey, or {@code null}
	 * @return options in the order they are tried
	 */
	public @NotNull List<JourneyOption> resolve(
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType,
			@Nullable JourneyOverrideItem override
	) {
		Set<String> excludedProviders = normalized(override != null ? override.getExcludedProviders() : null);
		Set<String> excludedMethods = normalized(override != null ? override.getExcludedMethods() : null);

		List<String> providerIds = providerOperations.eligibleProviders(context, pipelineType).stream()
				.map(this::providerId)
				.filter(id -> !id.isBlank())
				.filter(id -> !excludedProviders.contains(normalize(id)))
				.toList();
		List<String> ordered = pipelineType == PipelineType.MIGRATION
				? migrationTarget(context, providerIds)
				: providerSelector.rank(context.getSelection(), providerIds);

		JourneyRegistry registry = registry(pipelineType);
		List<JourneyOption> options = new ArrayList<>();
		for (String providerId : ordered) {
			List<ProviderMethod> methods = registry.methods(providerId);
			if (methods.isEmpty()) {
				options.add(new JourneyOption(providerId, null));
				continue;
			}

			for (ProviderMethod method : orderedMethods(context, pipelineType, providerId, methods)) {
				if (excludedMethods.contains(method.id())) continue;
				if (!method.getAvailability().isAvailable(context)) continue;

				options.add(new JourneyOption(providerId, method));
			}
		}

		return List.copyOf(options);
	}

	/**
	 * Orders a provider's methods by its provider entry, leaving out those the entry does not name, and puts the
	 * method the connection already runs first.
	 */
	private @NotNull List<ProviderMethod> orderedMethods(
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType,
			@NotNull String providerId,
			@NotNull List<ProviderMethod> methods
	) {
		List<ProviderMethod> ordered = new ArrayList<>(methods);
		List<String> configured = configuredMethods(providerId, pipelineType);
		if (configured != null) {
			List<String> names = configured.stream().map(this::normalize).toList();
			ordered.removeIf(method -> !names.contains(normalize(method.getName())));
			ordered.sort(Comparator.comparingInt(method -> names.indexOf(normalize(method.getName()))));
		}

		String running = runningMethod(context, providerId);
		if (running != null)
			ordered.sort(Comparator.comparing(method -> !normalize(method.getName()).equals(running)));

		return ordered;
	}

	private @Nullable List<String> configuredMethods(@NotNull String providerId, @NotNull PipelineType pipelineType) {
		return providersProvider.get().getProviders().stream()
				.filter(entry -> providerId.equalsIgnoreCase(entry.getId()))
				.map(Providers.ProviderEntry::getMethods)
				.filter(Objects::nonNull)
				.map(methods -> methods.of(pipelineType))
				.filter(Objects::nonNull)
				.findFirst()
				.orElse(null);
	}

	private @Nullable String runningMethod(@NotNull ScenarioContext context, @NotNull String providerId) {
		ProviderContext provider = context.getProvider();
		if (provider == null || provider.getMethod() == null || provider.getMethod().isBlank()) return null;
		if (!providerId.equalsIgnoreCase(provider.getProviderId())) return null;

		return normalize(provider.getMethod());
	}

	private @NotNull List<String> migrationTarget(@NotNull ScenarioContext context, @NotNull List<String> providerIds) {
		if (!(context instanceof MigrationContext migration) || migration.getTargetProviderId() == null)
			return List.of();

		String targetProviderId = migration.getTargetProviderId();
		return providerIds.stream()
				.filter(targetProviderId::equalsIgnoreCase)
				.limit(1)
				.toList();
	}

	private @NotNull JourneyRegistry registry(@NotNull PipelineType pipelineType) {
		if (pipelineType == PipelineType.REGISTRATION) return registrationJourneyRegistry.get();
		if (pipelineType == PipelineType.MIGRATION) return migrationJourneyRegistry.get();

		return authenticationJourneyRegistry.get();
	}

	private @NotNull String providerId(@Nullable InternalProvider provider) {
		if (provider == null || provider.getDescriptor() == null) return "";

		return provider.getDescriptor().getId();
	}

	private @NotNull Set<String> normalized(@Nullable List<String> values) {
		if (values == null || values.isEmpty()) return Set.of();

		Set<String> normalized = new HashSet<>();
		for (String value : values)
			if (value != null && !value.isBlank()) normalized.add(normalize(value));

		return normalized;
	}

	private @NotNull String normalize(@NotNull String value) {
		return value.trim().toLowerCase(Locale.ROOT);
	}
}
