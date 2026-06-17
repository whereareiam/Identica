package me.whereareiam.identica.common.provider.runtime.binding;

import me.whereareiam.identica.common.provider.runtime.binding.registration.CompositeRegistration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class ProviderContributionBinders {
	private final Map<Class<?>, ProviderContributionBinder<?>> bindersByType;

	public ProviderContributionBinders(@NotNull Collection<? extends ProviderContributionBinder<?>> binders) {
		Map<Class<?>, ProviderContributionBinder<?>> resolved = new LinkedHashMap<>();
		for (ProviderContributionBinder<?> binder : binders) {
			if (binder == null) continue;

			resolved.put(binder.contributionType(), binder);
		}

		this.bindersByType = Map.copyOf(resolved);
	}

	public <T> void bindAll(
			@NotNull CompositeRegistration registration,
			@NotNull Class<T> contributionType,
			@Nullable Set<T> contributions
	) {
		if (contributions == null || contributions.isEmpty()) return;

		ProviderContributionBinder<T> binder = resolve(contributionType);
		for (T contribution : contributions) {
			if (contribution == null) continue;

			registration.add(binder.bind(contribution));
		}
	}

	@SuppressWarnings("unchecked")
	private <T> @NotNull ProviderContributionBinder<T> resolve(@NotNull Class<T> contributionType) {
		ProviderContributionBinder<?> binder = bindersByType.get(contributionType);
		if (binder == null) throw new IllegalStateException(
				"No provider snapshot binder registered for " + contributionType.getName()
		);

		return (ProviderContributionBinder<T>) binder;
	}
}
