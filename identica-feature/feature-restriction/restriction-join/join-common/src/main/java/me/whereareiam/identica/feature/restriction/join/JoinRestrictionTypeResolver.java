package me.whereareiam.identica.feature.restriction.join;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.feature.restriction.RestrictionTypeResolver;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionFeatures;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionProvidersProvider;
import me.whereareiam.identica.feature.restriction.model.RestrictionTypeState;
import me.whereareiam.identica.feature.restriction.type.RestrictionSignal;
import me.whereareiam.identica.feature.restriction.type.RestrictionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JoinRestrictionTypeResolver implements RestrictionTypeResolver {
	private final me.whereareiam.identica.feature.FeatureRegistry features;
	private final com.google.inject.Provider<me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionSettings> settings;
	private final ProviderManager providerManager;
	private final JoinRestrictionProvidersProvider providersProvider;

	@Override
	public @NotNull RestrictionType type() {
		return JoinRestrictionType.TYPE;
	}

	@Override
	public @NotNull Optional<RestrictionTypeState> resolve(@Nullable String providerId) {
		InternalProvider provider = findProvider(providerId);
		if (provider == null || provider.getDescriptor() == null) return Optional.empty();
		if (!provider.getDescriptor().supportsFeature(JoinRestrictionFeatureId.ID)) return Optional.empty();

		String resolvedProviderId = provider.getDescriptor().getId();
		Providers.ProviderEntry entry = findEntry(resolvedProviderId);
		JoinRestrictionFeatures.Restriction.Join join = join(entry);
		Set<RestrictionSignal> allow = allow(join);
		return Optional.of(new RestrictionTypeState(resolvedProviderId,
				features.isEnabled(resolvedProviderId, "restriction") && features.isEnabled(resolvedProviderId, JoinRestrictionFeatureId.ID),
				true, allow));
	}

	@Override
	public @NotNull List<RestrictionTypeState> resolveAll() {
		List<RestrictionTypeState> states = new ArrayList<>();
		for (InternalProvider provider : providerManager.getProviders()) {
			if (provider == null || provider.getDescriptor() == null) continue;
			if (!provider.getDescriptor().supportsFeature(JoinRestrictionFeatureId.ID)) continue;

			resolve(provider.getDescriptor().getId()).ifPresent(states::add);
		}

		return List.copyOf(states);
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

	private @Nullable Providers.ProviderEntry findEntry(@NotNull String providerId) {
		for (Providers.ProviderEntry entry : providersProvider.get().getProviders()) {
			if (entry == null || entry.getId().isBlank()) continue;
			if (entry.getId().equalsIgnoreCase(providerId))
				return entry;
		}

		return null;
	}

	private @Nullable JoinRestrictionFeatures.Restriction.Join join(@Nullable Providers.ProviderEntry entry) {
		if (entry == null) return null;
		if (!(entry.getFeatures() instanceof JoinRestrictionFeatures features)) return null;
		if (features.getRestriction() == null) return null;

		return features.getRestriction().getJoin();
	}

	private @NotNull Set<RestrictionSignal> allow(@Nullable JoinRestrictionFeatures.Restriction.Join join) {
		LinkedHashSet<RestrictionSignal> resolved = new LinkedHashSet<>();
		for (String signal : join != null && join.getAllow() != null ? join.getAllow() : settings.get().getAllow()) {
			if (signal == null || signal.isBlank()) continue;
			resolved.add(RestrictionSignal.of(signal.trim().toLowerCase(Locale.ROOT)));
		}

		return Set.copyOf(resolved);
	}
}
