package me.whereareiam.identica.engine.pipeline.prepare.runtime;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Finds the account a connection belongs to before its profile is known, without reserving or changing anything.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class KnownAccountResolver {
	private final ProviderOperations providerOperations;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final AccountPersistenceService accountPersistenceService;

	/**
	 * Returns the preferred provider link of the account the connection belongs to: the account linked to the subject
	 * a provider resolves for the connection, or else the only account that uses its username.
	 *
	 * @param identity connection identity
	 * @return preferred link, or {@code null} when no known account matches
	 */
	public @Nullable AccountProviderLink resolvePreferredLink(@NotNull ConnectionIdentity identity) {
		UUID accountUniqueId = resolveAccountUniqueId(identity);
		if (accountUniqueId == null) return null;

		return providerOperations.selectPreferredLink(providerLinkPersistenceService.findByUniqueId(accountUniqueId));
	}

	private @Nullable UUID resolveAccountUniqueId(@NotNull ConnectionIdentity identity) {
		SubjectResolution subject = providerOperations.discoverSubject(SubjectResolveContext.builder()
				.identity(identity)
				.build());
		if (subject != null && subject.getProviderSubject() != null && !subject.getProviderSubject().isBlank()) {
			AccountProviderLink link = providerLinkPersistenceService
					.findBySubject(subject.getProviderId(), subject.getProviderSubject())
					.orElse(null);
			if (link != null) return link.getUniqueId();
		}

		List<Account> accounts = accountPersistenceService.findByUsername(identity.getUsername());

		return accounts.size() == 1 ? accounts.getFirst().getUniqueId() : null;
	}
}
