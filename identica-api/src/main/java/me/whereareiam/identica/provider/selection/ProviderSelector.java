package me.whereareiam.identica.provider.selection;

import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderSelection;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Ranks provider claims. It is the only way to change a {@link ProviderSelection}: every change returns a new
 * selection ranked again, so a later claim never overwrites an earlier one.
 *
 * <p>Claims rank by origin first: a pending migration, then a verified account link, an entrypoint hostname, a
 * verified subject without an account, the player's choice, a name match and discovery. Claims of the same origin
 * rank by provider priority, then by provider id. Only enabled providers that were not excluded can win.</p>
 *
 * <pre>{@code
 * ProviderSelection chosen = selector.claim(
 *         context.getSelection(),
 *         List.of(ProviderClaim.of("alpha", ProviderClaimOrigin.PLAYER_CHOICE))
 * );
 * context.setSelection(chosen);
 * }</pre>
 */
public interface ProviderSelector {
	/**
	 * Adds claims to a selection and ranks it again. A claim of the same provider and origin as an existing one
	 * replaces it.
	 *
	 * @param selection current selection
	 * @param claims claims to add
	 * @return ranked selection
	 */
	@NotNull ProviderSelection claim(@NotNull ProviderSelection selection, @NotNull Collection<ProviderClaim> claims);

	/**
	 * Removes every claim of an origin, such as the claim of a cancelled migration, and ranks the rest again.
	 *
	 * @param selection current selection
	 * @param origin origin whose claims are withdrawn
	 * @return ranked selection
	 */
	@NotNull ProviderSelection revoke(@NotNull ProviderSelection selection, @NotNull ProviderClaimOrigin origin);

	/**
	 * Excludes a provider that failed for the connection; its claims can no longer win.
	 *
	 * @param selection current selection
	 * @param providerId provider to exclude
	 * @return ranked selection
	 */
	@NotNull ProviderSelection exclude(@NotNull ProviderSelection selection, @NotNull String providerId);

	/**
	 * Orders candidate providers by the selection: providers with claims by their strongest claim, then the others
	 * by priority and id. Excluded providers are left out.
	 *
	 * @param selection selection of the connection
	 * @param candidateProviderIds providers that may handle the connection, such as the eligible ones
	 * @return candidate ids, strongest first
	 */
	@NotNull List<String> rank(@NotNull ProviderSelection selection, @NotNull Collection<String> candidateProviderIds);

	/**
	 * Returns the strongest claim that names one of the candidate providers.
	 *
	 * @param selection selection of the connection
	 * @param candidateProviderIds providers that may handle the connection, such as the offered ones
	 * @return winning claim among the candidates, or {@code null} when none of them is claimed
	 */
	@Nullable ProviderClaim winner(@NotNull ProviderSelection selection, @NotNull Collection<String> candidateProviderIds);

	/**
	 * Returns whether an origin ranks at least as high as another.
	 *
	 * @param origin origin to compare
	 * @param threshold origin it must reach
	 * @return {@code true} when {@code origin} is {@code threshold} or stronger
	 */
	boolean isAtLeast(@Nullable ProviderClaimOrigin origin, @NotNull ProviderClaimOrigin threshold);
}
