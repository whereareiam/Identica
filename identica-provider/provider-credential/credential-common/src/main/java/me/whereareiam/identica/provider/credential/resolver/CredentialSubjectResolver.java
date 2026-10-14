package me.whereareiam.identica.provider.credential.resolver;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.provider.credential.CredentialConstants;
import me.whereareiam.identica.provider.subject.SubjectResolution;
import me.whereareiam.identica.provider.subject.SubjectResolveContext;
import me.whereareiam.identica.provider.subject.SubjectResolver;
import me.whereareiam.identica.util.UniqueIdGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Resolves the Credential subject of a username. A subject is the offline-mode UUID of the username a Credential
 * account was registered under, and that UUID depends on the username's case. Usernames are unique without regard
 * to case, so a player who joins as {@code mrbeast} owns the Credential account registered as {@code MrBeast}: the
 * subject of that account is resolved instead of a new one.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class CredentialSubjectResolver implements SubjectResolver {
	private final AccountService accountService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;

	@Override
	public boolean supports(@NotNull SubjectResolveContext context) {
		String username = context.getUsername();
		return username != null && !username.isBlank();
	}

	@Override
	public @Nullable SubjectResolution resolve(@NotNull SubjectResolveContext context) {
		String subject = resolve(context.getUsername());
		if (subject == null) return null;

		return SubjectResolution.builder()
				.providerId(CredentialConstants.PROVIDER_ID)
				.providerSubject(subject)
				.build();
	}

	/**
	 * Returns the subject of the Credential account that holds the username in any case, or else the subject a new
	 * Credential account of the username gets. Accounts from before usernames were unique may share a username in
	 * several cases; when more than one of them has Credential, the username's own subject is returned.
	 *
	 * @param username username a player joins with or an administrator names
	 * @return the subject, or {@code null} for a blank username
	 */
	public @Nullable String resolve(@Nullable String username) {
		if (username == null || username.isBlank()) return null;

		UUID offline = UniqueIdGenerator.offlinePlayerUniqueId(username);
		if (offline == null) return null;

		String subject = offline.toString();
		if (providerLinkPersistenceService.findBySubject(CredentialConstants.PROVIDER_ID, subject).isPresent())
			return subject;

		String registered = null;
		for (Account account : accountService.find(username)) {
			String linked = credentialSubjectOf(account);
			if (linked == null) continue;
			if (registered != null && !registered.equals(linked)) return subject;

			registered = linked;
		}

		return registered != null ? registered : subject;
	}

	private @Nullable String credentialSubjectOf(@NotNull Account account) {
		return providerLinkPersistenceService
				.findByUniqueIdAndProviderId(account.getUniqueId(), CredentialConstants.PROVIDER_ID)
				.map(AccountProviderLink::getProviderSubject)
				.orElse(null);
	}

	@Override
	public int priority() {
		return 10;
	}
}
