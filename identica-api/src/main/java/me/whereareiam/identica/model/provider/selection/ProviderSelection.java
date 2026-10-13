package me.whereareiam.identica.model.provider.selection;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.provider.selection.ProviderSelector;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Which provider handles a connection, and why.
 *
 * <p>A selection holds the winning {@link ProviderClaim}, the claims it beat in ranked order, and the providers
 * excluded after they failed for the connection. It is computed once per connection, while it is prepared, and kept
 * with its scenario; a later claim, such as the player's own choice, is added through {@link ProviderSelector#claim}
 * and ranks again instead of replacing the winner. The origin of the selection is always the winning claim's.</p>
 *
 * <pre>{@code
 * ProviderSelection selection = context.getSelection();
 * if (selection.isWonBy("alpha")) {
 *     ProviderClaimOrigin origin = selection.getOrigin();
 * }
 * }</pre>
 */
@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ProviderSelection {
	/**
	 * Strongest claim of a provider that can handle the connection.
	 */
	private @Nullable ProviderClaim winner;
	/**
	 * Claims the winner beat, strongest first. Claims of providers that cannot win, because they are excluded or not
	 * enabled, are kept here too.
	 */
	private @NotNull List<ProviderClaim> beaten = List.of();
	/**
	 * Providers excluded after they failed for the connection, in lower case.
	 */
	private @NotNull List<String> excludedProviderIds = List.of();

	/**
	 * Returns a selection without claims.
	 *
	 * @return empty selection
	 */
	public static @NotNull ProviderSelection empty() {
		return new ProviderSelection(null, List.of(), List.of());
	}

	/**
	 * Creates a ranked selection. Only the engine's ranking decides the winner and the order of the other claims;
	 * other code changes a selection through {@link ProviderSelector}.
	 *
	 * @param winner winning claim, or {@code null} when no claim can win
	 * @param beaten the other claims, strongest first, including claims of providers that cannot win
	 * @param excludedProviderIds providers excluded for the connection, in lower case
	 * @return selection
	 */
	public static @NotNull ProviderSelection of(
			@Nullable ProviderClaim winner,
			@NotNull List<ProviderClaim> beaten,
			@NotNull List<String> excludedProviderIds
	) {
		return new ProviderSelection(winner, List.copyOf(beaten), List.copyOf(excludedProviderIds));
	}

	/**
	 * Returns the id of the winning provider.
	 *
	 * @return provider id, or {@code null} when nothing claims the connection
	 */
	public @Nullable String getWinnerProviderId() {
		return winner != null ? winner.getProviderId() : null;
	}

	/**
	 * Returns the origin of the winning claim.
	 *
	 * @return origin, or {@code null} when nothing claims the connection
	 */
	public @Nullable ProviderClaimOrigin getOrigin() {
		return winner != null ? winner.getOrigin() : null;
	}

	/**
	 * Returns whether the provider won the selection, ignoring case.
	 *
	 * @param providerId provider id
	 * @return {@code true} when the winning claim names the provider
	 */
	public boolean isWonBy(@Nullable String providerId) {
		return winner != null && winner.names(providerId);
	}

	/**
	 * Returns every claim, the winner first and then the claims it beat in ranked order.
	 *
	 * @return ranked claims
	 */
	public @NotNull List<ProviderClaim> claims() {
		List<ProviderClaim> claims = new ArrayList<>();
		if (winner != null) claims.add(winner);
		claims.addAll(beaten);

		return List.copyOf(claims);
	}

	/**
	 * Returns whether a claim of the origin names the provider.
	 *
	 * @param providerId provider id
	 * @param origin claim origin
	 * @return {@code true} when such a claim exists
	 */
	public boolean hasClaim(@Nullable String providerId, @NotNull ProviderClaimOrigin origin) {
		return claims().stream().anyMatch(claim -> claim.getOrigin() == origin && claim.names(providerId));
	}

	/**
	 * Returns the strongest claim of the origin.
	 *
	 * @param origin claim origin
	 * @return claim, or empty when no claim has the origin
	 */
	public @NotNull Optional<ProviderClaim> findClaim(@NotNull ProviderClaimOrigin origin) {
		return claims().stream().filter(claim -> claim.getOrigin() == origin).findFirst();
	}

	/**
	 * Returns the subject the provider verified for this connection.
	 *
	 * @param providerId provider id
	 * @return verified subject, or empty when the provider proved nothing
	 */
	public @NotNull Optional<String> findVerifiedSubject(@Nullable String providerId) {
		return claims().stream()
				.filter(claim -> claim.hasProof() && claim.names(providerId))
				.map(ProviderClaim::getVerifiedSubject)
				.findFirst();
	}

	/**
	 * Returns whether the provider was excluded after it failed for the connection.
	 *
	 * @param providerId provider id
	 * @return {@code true} when excluded
	 */
	public boolean isExcluded(@Nullable String providerId) {
		return providerId != null && excludedProviderIds.contains(providerId.trim().toLowerCase(Locale.ROOT));
	}
}
