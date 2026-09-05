package me.whereareiam.identica.feature.restriction;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.feature.ProviderFeatureContribution;
import me.whereareiam.identica.feature.restriction.contribution.ProviderRestrictionSignalContribution;
import me.whereareiam.identica.feature.restriction.model.RestrictionDecision;
import me.whereareiam.identica.feature.restriction.model.RestrictionEvaluationRequest;
import me.whereareiam.identica.feature.restriction.model.RestrictionStatus;
import me.whereareiam.identica.feature.restriction.model.RestrictionTypeState;
import me.whereareiam.identica.feature.restriction.registry.type.RestrictionTypeResolverRegistry;
import me.whereareiam.identica.feature.restriction.type.RestrictionSignal;
import me.whereareiam.identica.feature.restriction.type.RestrictionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Singleton
public class DefaultRestrictionService implements RestrictionService {
	private final RestrictionTypeResolverRegistry handlerRegistry;
	private final RestrictionActivationStore toggleStore;
	private final ProviderManager providerManager;

	@Inject
	public DefaultRestrictionService(
			RestrictionTypeResolverRegistry handlerRegistry,
			RestrictionActivationStore toggleStore,
			ProviderManager providerManager
	) {
		this.handlerRegistry = handlerRegistry;
		this.toggleStore = toggleStore;
		this.providerManager = providerManager;
	}

	@Override
	public @NotNull RestrictionStatus enable(@NotNull RestrictionType type, @Nullable String providerId) {
		RestrictionTypeState state = resolveState(type, providerId).orElseGet(() -> missing(type, providerId));
		if (!state.isEnabled() || !state.isConfigured())
			return RestrictionStatus.of(type, state.getProviderId(), false, state.isConfigured(), state.getAllow());

		toggleStore.enable(type, state.getProviderId());
		return RestrictionStatus.of(type, state.getProviderId(), true, true, state.getAllow());
	}

	@Override
	public @NotNull RestrictionStatus disable(@NotNull RestrictionType type, @Nullable String providerId) {
		RestrictionTypeState state = resolveState(type, providerId).orElseGet(() -> missing(type, providerId));
		toggleStore.disable(type, state.getProviderId());
		return RestrictionStatus.of(type, state.getProviderId(), false, state.isConfigured(), state.getAllow());
	}

	@Override
	public @NotNull Optional<RestrictionStatus> status(@NotNull RestrictionType type, @Nullable String providerId) {
		return resolveState(type, providerId)
				.map(state -> RestrictionStatus.of(type, state.getProviderId(), isActive(type, state), state.isConfigured(), state.getAllow()));
	}

	@Override
	public @NotNull List<RestrictionStatus> statuses(@NotNull RestrictionType type) {
		RestrictionTypeResolver handler = handlerRegistry.find(type.getId()).orElse(null);
		if (handler == null) return List.of();

		List<RestrictionStatus> statuses = new ArrayList<>();
		for (RestrictionTypeState state : handler.resolveAll())
			statuses.add(RestrictionStatus.of(type, state.getProviderId(), isActive(type, state), state.isConfigured(), state.getAllow()));

		return List.copyOf(statuses);
	}

	@Override
	public @NotNull RestrictionDecision evaluate(@NotNull RestrictionEvaluationRequest request) {
		RestrictionType type = request.getType();
		RestrictionTypeState state = resolveState(type, request.getProviderId()).orElseGet(() -> missing(type, request.getProviderId()));
		if (!isActive(type, state))
			return RestrictionDecision.of(type, state.getProviderId(), true, state.isConfigured(), false, state.getAllow(), Set.of());
		if (state.getAllow().isEmpty())
			return RestrictionDecision.of(type, state.getProviderId(), false, true, true, state.getAllow(), Set.of());

		Set<RestrictionSignal> matchedSignals = new LinkedHashSet<>();
		for (ProviderRestrictionSignalContribution contribution : resolveSignalContributions(request.getProviderId(), type)) {
			RestrictionSignal signal = contribution.signal();
			if (!state.getAllow().contains(signal)) continue;
			if (contribution.matches(request)) matchedSignals.add(signal);
		}

		if (!matchedSignals.isEmpty())
			return RestrictionDecision.of(type, state.getProviderId(), true, true, true, state.getAllow(), Set.copyOf(matchedSignals));

		return RestrictionDecision.of(type, state.getProviderId(), false, true, true, state.getAllow(), Set.of());
	}

	private @NotNull Optional<RestrictionTypeState> resolveState(@NotNull RestrictionType type, @Nullable String providerId) {
		RestrictionTypeResolver handler = handlerRegistry.find(type.getId()).orElse(null);
		if (handler == null) return Optional.empty();

		return handler.resolve(providerId);
	}

	private boolean isActive(@NotNull RestrictionType type, @NotNull RestrictionTypeState state) {
		return state.isEnabled() && state.isConfigured() && toggleStore.isActive(type, state.getProviderId());
	}

	private @NotNull List<ProviderRestrictionSignalContribution> resolveSignalContributions(
			@Nullable String providerId,
			@NotNull RestrictionType type
	) {
		InternalProvider provider = findProvider(providerId);
		if (provider == null || provider.getFeatureContributions() == null) return List.of();

		List<ProviderRestrictionSignalContribution> resolved = new ArrayList<>();
		for (ProviderFeatureContribution contribution : provider.getFeatureContributions()) {
			if (!(contribution instanceof ProviderRestrictionSignalContribution signalContribution)) continue;
			if (!signalContribution.restrictionType().equals(type)) continue;

			resolved.add(signalContribution);
		}

		return List.copyOf(resolved);
	}

	private @Nullable InternalProvider findProvider(@Nullable String providerId) {
		if (providerId == null || providerId.isBlank()) return null;
		for (InternalProvider provider : providerManager.getProviders()) {
			if (provider == null || provider.getDescriptor() == null) continue;
			if (provider.getDescriptor().getId().equalsIgnoreCase(providerId))
				return provider;
		}
		return null;
	}

	private @NotNull RestrictionTypeState missing(@NotNull RestrictionType type, @Nullable String providerId) {
		String resolvedProviderId = providerId != null && !providerId.isBlank() ? providerId : "";
		return new RestrictionTypeState(resolvedProviderId, false, false, Set.of());
	}
}
