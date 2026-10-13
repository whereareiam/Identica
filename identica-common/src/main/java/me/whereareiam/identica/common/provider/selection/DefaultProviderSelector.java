package me.whereareiam.identica.common.provider.selection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.provider.selection.ProviderSelector;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Changes provider selections through {@link ProviderClaimRanking}.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultProviderSelector implements ProviderSelector {
	private final ProviderClaimRanking ranking;

	@Override
	public @NotNull ProviderSelection claim(
			@NotNull ProviderSelection selection,
			@NotNull Collection<ProviderClaim> claims
	) {
		Map<String, ProviderClaim> merged = new LinkedHashMap<>();
		for (ProviderClaim claim : selection.claims())
			merged.put(key(claim), claim);
		for (ProviderClaim claim : claims)
			if (claim != null) merged.put(key(claim), claim);

		return rank(merged.values(), selection.getExcludedProviderIds());
	}

	@Override
	public @NotNull ProviderSelection revoke(@NotNull ProviderSelection selection, @NotNull ProviderClaimOrigin origin) {
		List<ProviderClaim> kept = selection.claims().stream()
				.filter(claim -> claim.getOrigin() != origin)
				.toList();

		return rank(kept, selection.getExcludedProviderIds());
	}

	@Override
	public @NotNull ProviderSelection exclude(@NotNull ProviderSelection selection, @NotNull String providerId) {
		Set<String> excluded = new LinkedHashSet<>(selection.getExcludedProviderIds());
		excluded.add(normalize(providerId));

		return rank(selection.claims(), List.copyOf(excluded));
	}

	@Override
	public @NotNull List<String> rank(
			@NotNull ProviderSelection selection,
			@NotNull Collection<String> candidateProviderIds
	) {
		Comparator<String> order = Comparator.<String>comparingInt(id -> strongestPrecedence(selection, id))
				.thenComparing(ranking.providers());

		return candidateProviderIds.stream()
				.filter(id -> id != null && !id.isBlank())
				.filter(id -> !selection.isExcluded(id))
				.distinct()
				.sorted(order)
				.toList();
	}

	@Override
	public @Nullable ProviderClaim winner(
			@NotNull ProviderSelection selection,
			@NotNull Collection<String> candidateProviderIds
	) {
		return selection.claims().stream()
				.filter(claim -> !selection.isExcluded(claim.getProviderId()))
				.filter(claim -> candidateProviderIds.stream().anyMatch(claim::names))
				.min(ranking.claims())
				.orElse(null);
	}

	@Override
	public boolean isAtLeast(@Nullable ProviderClaimOrigin origin, @NotNull ProviderClaimOrigin threshold) {
		return origin != null && ranking.precedence(origin) <= ranking.precedence(threshold);
	}

	private @NotNull ProviderSelection rank(@NotNull Collection<ProviderClaim> claims, @NotNull List<String> excluded) {
		List<ProviderClaim> ranked = new ArrayList<>(claims);
		ranked.sort(ranking.claims());

		Optional<ProviderClaim> winner = ranked.stream()
				.filter(claim -> !excluded.contains(normalize(claim.getProviderId())))
				.filter(claim -> ranking.isEnabled(claim.getProviderId()))
				.findFirst();
		winner.ifPresent(ranked::remove);

		return ProviderSelection.of(winner.orElse(null), ranked, excluded);
	}

	/**
	 * The rank of the provider's strongest claim; a provider without claims ranks after every claimed one.
	 */
	private int strongestPrecedence(@NotNull ProviderSelection selection, @NotNull String providerId) {
		return selection.claims().stream()
				.filter(claim -> claim.names(providerId))
				.mapToInt(claim -> ranking.precedence(claim.getOrigin()))
				.min()
				.orElse(Integer.MAX_VALUE);
	}

	private static @NotNull String key(@NotNull ProviderClaim claim) {
		return normalize(claim.getProviderId()) + "|" + claim.getOrigin();
	}

	private static @NotNull String normalize(@NotNull String providerId) {
		return providerId.trim().toLowerCase(Locale.ROOT);
	}
}
