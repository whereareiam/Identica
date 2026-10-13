package me.whereareiam.identica.provider.selection;

import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderClaimRequest;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Contributes claims to the selection of a connection's provider.
 *
 * <p>Identica asks every registered source at each preparation stage and ranks the claims together; a source never
 * decides the winner itself. Identica's own sources cover pending migrations, verified subjects, account links,
 * entrypoint hostnames, the player's choice, name matches and discovery. A provider adds its own by binding it in
 * one of its modules:</p>
 *
 * <pre>{@code
 * Multibinder.newSetBinder(binder(), ProviderClaimSource.class)
 *         .addBinding()
 *         .to(AlphaClaimSource.class);
 * }</pre>
 */
@FunctionalInterface
public interface ProviderClaimSource {
	/**
	 * Returns the claims this source makes for the connection.
	 *
	 * @param request what is known about the connection
	 * @return claims, empty when this source has none
	 */
	@NotNull Collection<ProviderClaim> claims(@NotNull ProviderClaimRequest request);
}
