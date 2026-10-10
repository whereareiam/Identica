package me.whereareiam.identica.common.provider;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.provider.ProviderEligibilityEvent;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.pipeline.journey.JourneyPlan;
import me.whereareiam.identica.model.pipeline.journey.stage.step.JourneyStep;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.model.provider.ResolvedEntrypoint;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.registry.JourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.AuthenticationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.MigrationJourneyRegistry;
import me.whereareiam.identica.pipeline.journey.registry.type.RegistrationJourneyRegistry;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.eligibility.ProviderEligibilityResolver;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import me.whereareiam.identica.provider.subject.SubjectResolver;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.provider.ProviderState;
import me.whereareiam.identica.util.NetworkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Stream;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultProviderOperations implements ProviderOperations {
	private static final Comparator<EntrypointCandidate> ENTRYPOINT_ORDER =
			Comparator.comparingInt(EntrypointCandidate::priority)
					.thenComparing(EntrypointCandidate::providerId, String.CASE_INSENSITIVE_ORDER.reversed());
	private final ProviderManager providerManager;
	private final AuthenticationJourneyRegistry authenticationJourneyRegistry;
	private final RegistrationJourneyRegistry registrationJourneyRegistry;
	private final MigrationJourneyRegistry migrationJourneyRegistry;
	private final Provider<Providers> providersProvider;
	private final EventManager eventManager;

	@Override
	public @Nullable AccountProviderLink selectPreferredLink(@NotNull List<AccountProviderLink> links) {
		List<AccountProviderLink> candidates = links.stream()
				.filter(link -> link != null && !isBlank(link.getProviderId()))
				.toList();
		List<AccountProviderLink> primaries = candidates.stream()
				.filter(AccountProviderLink::isPrimaryLink)
				.toList();

		return (primaries.isEmpty() ? candidates : primaries).stream()
				.max(Comparator.comparingInt(this::providerPriority)
						.thenComparing(AccountProviderLink::getProviderId, String.CASE_INSENSITIVE_ORDER))
				.orElse(null);
	}

	@Override
	public @Nullable SubjectResolution discoverSubject(@NotNull SubjectResolveContext context) {
		return resolveSelectedSubject(null, context);
	}

	@Override
	public @Nullable SubjectResolution resolveSelectedSubject(
			@Nullable String providerId,
			@NotNull SubjectResolveContext context
	) {
		List<InternalProvider> sorted = sortedEnabledProviders();
		if (sorted.isEmpty()) return null;

		String normalizedProviderId = normalizeProviderId(providerId);
		for (InternalProvider provider : sorted) {
			if (provider == null) continue;
			ProviderDescriptor descriptor = provider.getDescriptor();
			if (descriptor == null) continue;
			if (normalizedProviderId != null && !normalizedProviderId.equalsIgnoreCase(normalizeProviderId(descriptor.getId())))
				continue;

			SubjectResolution resolution = resolveFromProvider(provider, context);
			if (resolution != null)
				return resolution;
		}

		return null;
	}

	@Override
	public @Nullable ResolvedEntrypoint resolveEntrypoint(@Nullable String host, int port) {
		String normalizedHost = NetworkUtil.normalizeHost(host);
		if (normalizedHost == null) return null;

		return streamEntrypoints(providersProvider.get())
				.filter(candidate -> normalizedHost.equals(candidate.host()))
				.filter(candidate -> matchesPort(candidate.port(), port))
				.max(ENTRYPOINT_ORDER)
				.map(candidate -> ResolvedEntrypoint.builder()
						.providerId(candidate.providerId())
						.host(candidate.host())
						.port(candidate.port())
						.build())
				.orElse(null);
	}

	@Override
	public @Nullable String displayEntrypoint(@Nullable String providerId) {
		String normalizedProviderId = normalizeProviderId(providerId);
		if (normalizedProviderId == null) return null;

		return streamEntries(providersProvider.get())
				.filter(entry -> normalizedProviderId.equalsIgnoreCase(normalizeProviderId(entry.getId())))
				.flatMap(entry -> entry.getEntrypoints().stream().filter(Objects::nonNull))
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.findFirst()
				.orElse(null);
	}

	@Override
	public @Nullable String displayProviderName(@Nullable String providerId) {
		String normalizedProviderId = normalizeProviderId(providerId);
		if (normalizedProviderId == null) return null;

		String configured = streamEntries(providersProvider.get())
				.filter(entry -> normalizedProviderId.equalsIgnoreCase(normalizeProviderId(entry.getId())))
				.map(Providers.ProviderEntry::getDisplayName)
				.filter(Objects::nonNull)
				.map(String::trim)
				.filter(value -> !value.isBlank())
				.findFirst()
				.orElse(null);

		if (configured != null) return configured;

		List<InternalProvider> providers = providerManager.getProviders();
		if (providers != null) {
			for (InternalProvider provider : providers) {
				if (provider == null) continue;
				ProviderDescriptor descriptor = provider.getDescriptor();
				if (descriptor == null) continue;
				if (!normalizedProviderId.equalsIgnoreCase(normalizeProviderId(descriptor.getId())))
					continue;

				String name = descriptor.getName();
				if (!name.isBlank())
					return name.trim();
			}
		}

		return normalizedProviderId;
	}

	@Override
	public boolean hasEntrypoints(@Nullable String providerId) {
		return displayEntrypoint(providerId) != null;
	}

	@Override
	public @NotNull List<InternalProvider> eligibleProviders(
			@NotNull ScenarioContext context,
			@NotNull JourneyMode journeyMode
	) {
		return eligibleProviders(context, PipelineType.AUTHENTICATION, journeyMode);
	}

	@Override
	public @NotNull List<InternalProvider> eligibleProviders(
			@NotNull ScenarioContext context,
			@NotNull PipelineType pipelineType,
			@NotNull JourneyMode journeyMode
	) {
		List<InternalProvider> providers = sortedEnabledProviders();
		if (providers.isEmpty())
			return List.of();

		List<InternalProvider> eligible = new ArrayList<>();
		for (InternalProvider provider : providers) {
			if (provider == null) continue;
			if (isEligible(context, provider, pipelineType, journeyMode))
				eligible.add(provider);
		}

		return List.copyOf(eligible);
	}

	@Override
	public boolean isEligible(
			@NotNull ScenarioContext context,
			@NotNull InternalProvider provider,
			@NotNull JourneyMode journeyMode
	) {
		return isEligible(context, provider, PipelineType.AUTHENTICATION, journeyMode);
	}

	@Override
	public boolean isEligible(
			@NotNull ScenarioContext context,
			@NotNull InternalProvider provider,
			@NotNull PipelineType pipelineType,
			@NotNull JourneyMode journeyMode
	) {
		if (provider.getState() != ProviderState.ENABLED) return false;

		ProviderDescriptor descriptor = provider.getDescriptor();
		if (descriptor == null || isBlank(descriptor.getId())) return false;
		if (!supportsJourney(context, provider, pipelineType, journeyMode)) return false;
		if (!resolversAllow(context, provider, journeyMode)) return false;

		ProviderEligibilityEvent event = new ProviderEligibilityEvent(context, provider, journeyMode);
		eventManager.call(event);

		return !event.isCancelled();
	}

	private boolean supportsJourney(
			@NotNull ScenarioContext context,
			@NotNull InternalProvider provider,
			@NotNull PipelineType pipelineType,
			@NotNull JourneyMode journeyMode
	) {
		JourneyRegistry journeyRegistry = pipelineType == PipelineType.REGISTRATION
				? registrationJourneyRegistry
				: pipelineType == PipelineType.MIGRATION
						? migrationJourneyRegistry
						: authenticationJourneyRegistry;

		if (journeyRegistry == null) return false;
		JourneyPlan plan = journeyRegistry.resolvePlan(
				context, pipelineType, journeyMode, provider.getDescriptor().getId()
		);

		boolean hasProviderSteps = false;
		for (JourneyPlan.StageEntry stageEntry : plan.stages()) {
			if (stageEntry == null || !stageEntry.stage().providerStage())
				continue;

			if (stageEntry.steps().isEmpty()) continue;

			hasProviderSteps = true;
			if (journeyMode != JourneyMode.SEAMLESS) continue;
			for (JourneyStep step : stageEntry.steps()) {
				if (step.getJourneyModes().size() == 1 && step.getJourneyModes().contains(JourneyMode.INTERACTIVE))
					return false;
			}
		}

		return hasProviderSteps;
	}

	private boolean resolversAllow(
			@NotNull ScenarioContext context,
			@NotNull InternalProvider provider,
			@NotNull JourneyMode journeyMode
	) {
		Set<ProviderEligibilityResolver> resolvers = provider.getEligibilityResolvers();
		if (resolvers == null || resolvers.isEmpty()) return true;

		for (ProviderEligibilityResolver resolver : resolvers) {
			if (!resolver.isEligible(context, provider, journeyMode)) return false;
		}

		return true;
	}

	private @Nullable SubjectResolution resolveFromProvider(
			@NotNull InternalProvider provider,
			@NotNull SubjectResolveContext context
	) {
		Set<SubjectResolver> registered = provider.getSubjectResolvers();
		if (registered == null || registered.isEmpty()) return null;

		List<SubjectResolver> ordered = new ArrayList<>(registered);
		ordered.sort(Comparator.comparingInt(SubjectResolver::priority).reversed());

		for (SubjectResolver resolver : ordered) {
			if (resolver == null || !resolver.supports(context)) continue;

			SubjectResolution resolution = resolver.resolve(context);
			if (resolution == null) continue;

			String providerId = resolution.getProviderId();
			String providerSubject = resolution.getProviderSubject();
			if (isBlank(providerId) || isBlank(providerSubject)) continue;

			return resolution;
		}

		return null;
	}

	private int providerPriority(@NotNull AccountProviderLink link) {
		for (InternalProvider provider : providerManager.getProviders()) {
			if (provider == null || provider.getDescriptor() == null) continue;
			if (provider.getDescriptor().getId().equalsIgnoreCase(link.getProviderId()))
				return provider.getPriority();
		}

		return 0;
	}

	private @NotNull List<InternalProvider> sortedEnabledProviders() {
		List<InternalProvider> providers = providerManager.getProviders();
		if (providers == null || providers.isEmpty()) return List.of();

		return providers.stream()
				.filter(Objects::nonNull)
				.filter(provider -> provider.getState() == ProviderState.ENABLED)
				.filter(provider -> {
					ProviderDescriptor descriptor = provider.getDescriptor();
					return descriptor != null && !isBlank(descriptor.getId());
				})
				.sorted(Comparator.comparingInt(InternalProvider::getPriority)
						.reversed()
						.thenComparing(provider -> provider.getDescriptor().getId(), String.CASE_INSENSITIVE_ORDER))
				.toList();
	}

	private boolean isBlank(@Nullable String value) {
		return value == null || value.isBlank();
	}

	private @Nullable String normalizeProviderId(@Nullable String providerId) {
		if (providerId == null) return null;
		String trimmed = providerId.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private @NotNull Stream<Providers.ProviderEntry> streamEntries(@NotNull Providers providers) {
		List<Providers.ProviderEntry> entries = providers.getProviders();
		if (entries.isEmpty()) return Stream.empty();

		return entries.stream().filter(Objects::nonNull);
	}

	private @NotNull Stream<EntrypointCandidate> streamEntrypoints(@NotNull Providers providers) {
		return streamEntries(providers)
				.flatMap(entry -> {
					String providerId = normalizeProviderId(entry.getId());
					if (providerId == null) return Stream.empty();
					List<String> entrypoints = entry.getEntrypoints();
					if (entrypoints.isEmpty()) return Stream.empty();

					return entrypoints.stream()
							.filter(Objects::nonNull)
							.map(String::trim)
							.filter(value -> !value.isBlank())
							.map(NetworkUtil::parse)
							.filter(Objects::nonNull)
							.map(parsed -> new EntrypointCandidate(
									providerId,
									entry.getPriority(),
									parsed.host(),
									parsed.port()
							));
				});
	}

	private boolean matchesPort(@Nullable Integer entrypointPort, int requestedPort) {
		if (entrypointPort == null) return true;
		if (requestedPort < 0) return false;
		return entrypointPort == requestedPort;
	}

	private record EntrypointCandidate(
			@NotNull String providerId,
			int priority,
			@NotNull String host,
			@Nullable Integer port
	) {
	}
}
