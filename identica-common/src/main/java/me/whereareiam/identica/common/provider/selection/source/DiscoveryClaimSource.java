package me.whereareiam.identica.common.provider.selection.source;

import com.google.inject.Singleton;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderClaimRequest;
import me.whereareiam.identica.provider.selection.ProviderClaimSource;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Claims the connection for every provider that resolves a subject for it: as a verified subject when the provider
 * proved the client owns it, as discovery otherwise.
 */
@Singleton
public class DiscoveryClaimSource implements ProviderClaimSource {
	@Override
	public @NotNull Collection<ProviderClaim> claims(@NotNull ProviderClaimRequest request) {
		return request.getSubjects().stream()
				.map(DiscoveryClaimSource::claim)
				.toList();
	}

	private static @NotNull ProviderClaim claim(@NotNull SubjectResolution subject) {
		if (subject.isVerified()) return ProviderClaim.verified(subject.getProviderId(), subject.getProviderSubject());

		return ProviderClaim.of(subject.getProviderId(), ProviderClaimOrigin.DISCOVERY);
	}
}
