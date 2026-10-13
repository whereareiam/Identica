package me.whereareiam.identica.common.provider.selection.source;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.provider.ResolvedEntrypoint;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderClaimRequest;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.selection.ProviderClaimSource;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Claims the connection for the provider whose entrypoint hostname it arrived through.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class EntrypointClaimSource implements ProviderClaimSource {
	private final ProviderOperations providerOperations;

	@Override
	public @NotNull Collection<ProviderClaim> claims(@NotNull ProviderClaimRequest request) {
		ConnectionIdentity.Origin origin = request.getIdentity().getOrigin();
		if (origin == null || origin.getHost().isBlank()) return List.of();

		int port = origin.getPort() != null ? origin.getPort() : -1;
		ResolvedEntrypoint entrypoint = providerOperations.resolveEntrypoint(origin.getHost(), port);
		if (entrypoint == null) return List.of();

		return List.of(ProviderClaim.of(entrypoint.getProviderId(), ProviderClaimOrigin.HOSTNAME));
	}
}
