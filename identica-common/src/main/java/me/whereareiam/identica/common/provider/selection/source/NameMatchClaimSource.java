package me.whereareiam.identica.common.provider.selection.source;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.provider.selection.ProviderClaim;
import me.whereareiam.identica.model.provider.selection.ProviderClaimRequest;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.selection.ProviderClaimSource;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Claims the connection for the provider an account with its username prefers. Nothing about the client is proven
 * by a matching name.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class NameMatchClaimSource implements ProviderClaimSource {
	private final AccountPersistenceService accountPersistenceService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final ProviderOperations providerOperations;

	@Override
	public @NotNull Collection<ProviderClaim> claims(@NotNull ProviderClaimRequest request) {
		String username = request.getIdentity().getUsername();
		if (username.isBlank()) return List.of();

		return accountPersistenceService.findByUsername(username).stream()
				.filter(Objects::nonNull)
				.map(this::preferredLink)
				.filter(Objects::nonNull)
				.map(link -> ProviderClaim.of(link.getProviderId(), ProviderClaimOrigin.NAME_MATCH))
				.toList();
	}

	private AccountProviderLink preferredLink(@NotNull Account account) {
		return providerOperations.selectPreferredLink(providerLinkPersistenceService.findByUniqueId(account.getUniqueId()));
	}
}
