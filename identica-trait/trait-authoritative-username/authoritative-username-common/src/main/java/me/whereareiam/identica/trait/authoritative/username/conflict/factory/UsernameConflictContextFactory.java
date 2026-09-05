package me.whereareiam.identica.trait.authoritative.username.conflict.factory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.database.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.conflict.ConflictContext;
import me.whereareiam.identica.model.conflict.participant.ConflictParticipant;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.trait.authoritative.username.UsernameConflictSchema;
import me.whereareiam.identica.trait.authoritative.username.model.conflict.UsernameConflictSubject;
import me.whereareiam.identica.type.provider.ProviderOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UsernameConflictContextFactory {
	private final AccountPersistenceService accountPersistenceService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final ProviderProfilePersistenceService providerProfilePersistenceService;
	private final SessionService sessionService;

	public @Nullable ConflictContext create(@NotNull UsernameConflictSubject subject) {
		String candidate = subject.getCandidateUsername();
		if (candidate == null || candidate.isBlank()) return null;

		Account account = subject.getAccount();
		AccountProviderLink incomingLink = subject.getIncomingLink();
		if (account == null || incomingLink == null) return null;

		Account conflictAccount = resolveActiveConflict(account.getUniqueId(), candidate.trim()).orElse(null);
		if (conflictAccount == null) return null;

		AccountProviderLink existingLink = resolvePrimaryLink(conflictAccount.getUniqueId()).orElse(null);
		if (existingLink == null) return null;

		AccountProviderProfile existingProfile = providerProfilePersistenceService.findBySubject(
				existingLink.getProviderId(),
				existingLink.getProviderSubject()
		).orElse(null);

		ConflictContext context = ConflictContext.builder()
				.key("username")
				.hook(subject.hook())
				.build();
		context.putAttribute(UsernameConflictSchema.ATTRIBUTE_CANDIDATE_USERNAME, candidate.trim());
		context.putParticipant(buildIncomingParticipant(account, incomingLink, subject.getIncomingProfile()));
		context.putParticipant(buildExistingParticipant(conflictAccount, existingLink, existingProfile));

		ProviderContext providerContext = subject.getProviderContext();
		ProviderOrigin source = providerContext != null ? providerContext.getSource() : null;
		if (source != null)
			context.putAttribute(UsernameConflictSchema.ATTRIBUTE_ENTRYPOINT_SOURCE, source);

		resolvePrimaryLink(account.getUniqueId()).ifPresent(primaryIncoming ->
				context.putAttribute(
						UsernameConflictSchema.ATTRIBUTE_INCOMING_PRIMARY_PROVIDER_ID,
						primaryIncoming.getProviderId()
				)
		);
		return context;
	}

	private @NotNull ConflictParticipant buildIncomingParticipant(
			@NotNull Account account,
			@NotNull AccountProviderLink link,
			@Nullable AccountProviderProfile profile
	) {
		ConflictParticipant participant = ConflictParticipant.builder()
				.role(UsernameConflictSchema.ROLE_INCOMING)
				.build();
		participant.putAttribute(UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_ACCOUNT_UNIQUE_ID, account.getUniqueId());
		participant.putAttribute(UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_USERNAME, account.getUsername());
		participant.putAttribute(UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_PROVIDER_ID, link.getProviderId());
		participant.putAttribute(UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_PROVIDER_SUBJECT, link.getProviderSubject());
		if (profile != null)
			participant.putAttribute(
					UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_PROVIDER_USERNAME,
					profile.getProviderUsername()
			);

		return participant;
	}

	private @NotNull ConflictParticipant buildExistingParticipant(
			@NotNull Account account,
			@NotNull AccountProviderLink link,
			@Nullable AccountProviderProfile profile
	) {
		ConflictParticipant participant = ConflictParticipant.builder()
				.role(UsernameConflictSchema.ROLE_EXISTING)
				.build();
		participant.putAttribute(UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_ACCOUNT_UNIQUE_ID, account.getUniqueId());
		participant.putAttribute(UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_USERNAME, account.getUsername());
		participant.putAttribute(UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_PROVIDER_ID, link.getProviderId());
		participant.putAttribute(UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_PROVIDER_SUBJECT, link.getProviderSubject());
		if (profile != null)
			participant.putAttribute(
					UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_PROVIDER_USERNAME,
					profile.getProviderUsername()
			);

		return participant;
	}

	private @NotNull Optional<Account> resolveActiveConflict(@NotNull UUID uniqueId, @NotNull String username) {
		List<Account> accounts = accountPersistenceService.findByUsername(username);
		if (accounts.isEmpty()) return Optional.empty();

		for (Account account : accounts) {
			if (account == null) continue;
			if (account.getUniqueId().equals(uniqueId)) continue;
			if (isActive(account.getUniqueId())) return Optional.of(account);
		}

		return Optional.empty();
	}

	private @NotNull Optional<AccountProviderLink> resolvePrimaryLink(@NotNull UUID uniqueId) {
		List<AccountProviderLink> links = providerLinkPersistenceService.findByUniqueId(uniqueId);
		if (links.isEmpty()) return Optional.empty();

		for (AccountProviderLink link : links) {
			if (link != null && link.isPrimaryLink()) return Optional.of(link);
		}

		return Optional.ofNullable(links.getFirst());
	}

	private boolean isActive(@NotNull UUID uniqueId) {
		return sessionService.findByUniqueId(uniqueId)
				.thenApply(Optional::isPresent)
				.join();
	}
}
