package me.whereareiam.identica.common.provider.selection.source;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderClaimRequest;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.provider.selection.ProviderClaimSource;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Claims the connection for every provider that verified a subject already linked to an account.
 *
 * <p>A subject a provider only derived from what the client claims, such as its username, proves no more than a name
 * match, so it makes no account link claim: otherwise any client using a name would hold the account's link.</p>
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AccountLinkClaimSource implements ProviderClaimSource {
	private final ProviderLinkPersistenceService providerLinkPersistenceService;

	@Override
	public @NotNull Collection<ProviderClaim> claims(@NotNull ProviderClaimRequest request) {
		return request.getSubjects().stream()
				.filter(SubjectResolution::isVerified)
				.filter(subject -> providerLinkPersistenceService
						.findBySubject(subject.getProviderId(), subject.getProviderSubject())
						.isPresent())
				.map(subject -> ProviderClaim.of(subject.getProviderId(), ProviderClaimOrigin.ACCOUNT_LINK))
				.toList();
	}
}
