package me.whereareiam.identica.provider.credential.command;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.database.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.provider.credential.CredentialConstants;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.cryptography.PasswordCandidate;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.type.PasswordChangeReason;
import me.whereareiam.identica.provider.credential.util.PasswordRules;
import me.whereareiam.identica.util.UniqueIdGenerator;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ManagementCommand {
	private final Provider<CredentialMessages> messagesProvider;
	private final CredentialAccountService credentialService;
	private final CryptographyService cryptographyService;
	private final PasswordRules passwordPolicy;
	private final AccountService accountService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final ProviderProfilePersistenceService providerProfilePersistenceService;
	private final UniqueIdGenerator uniqueIdGenerator;

	@Definition("admin-force-register")
	@Command("identica credential register <username> <password>")
	public void forceRegister(
			@NotNull Actor sender,
			@Argument("username") String username,
			@Argument(value = "password", parser = "password") String password
	) {
		CredentialMessages.Commands.Admin messages = messagesProvider.get().getCommands().getAdmin();
		String providerSubject = resolveProviderSubject(username);
		if (providerSubject == null) {
			sendMessage(sender, messages.getNotFound());
			return;
		}

		if (credentialService.find(providerSubject).isPresent()) {
			sendMessage(sender, messages.getAlreadyRegistered());
			return;
		}

		String error = passwordPolicy.validate(password);
		if (error != null && !error.isBlank()) {
			sendMessage(sender, error);
			return;
		}

		PasswordCandidate candidate = cryptographyService.hash(password);
		if (candidate == null) {
			sendMessage(sender, messages.getNotFound());
			return;
		}

		if (!ensureAccount(username, providerSubject)) {
			sendMessage(sender, messages.getNotFound());
			return;
		}

		CredentialAccount credential = credentialService.register(
				providerSubject,
				candidate.getPasswordHash(),
				candidate.getHashingMethod(),
				PasswordChangeReason.ADMIN_SET
		).orElse(null);
		if (credential == null) {
			sendMessage(sender, messages.getNotFound());
			return;
		}

		sendMessage(sender, messages.getRegistered());
	}

	@Definition("admin-set-password")
	@Command("identica credential setpassword <username> <password>")
	public void setPassword(
			@NotNull Actor sender,
			@Argument("username") String username,
			@Argument(value = "password", parser = "password") String password
	) {
		CredentialMessages.Commands.Admin messages = messagesProvider.get().getCommands().getAdmin();
		String providerSubject = resolveProviderSubject(username);
		if (providerSubject == null) {
			sendMessage(sender, messages.getNotFound());
			return;
		}

		CredentialAccount credential = credentialService.find(providerSubject).orElse(null);
		if (credential == null) {
			sendMessage(sender, messages.getNotFound());
			return;
		}

		String error = passwordPolicy.validate(password);
		if (error != null && !error.isBlank()) {
			sendMessage(sender, error);
			return;
		}

		PasswordCandidate candidate = cryptographyService.hash(password);
		if (candidate != null) {
			credentialService.updatePassword(
					credential,
					candidate.getPasswordHash(),
					candidate.getHashingMethod(),
					PasswordChangeReason.ADMIN_SET
			);
		}
		sendMessage(sender, messages.getPasswordSet());
	}

	private void sendMessage(@NotNull Actor sender, @NotNull String message) {
		if (message.isBlank()) return;
		SerializerContent content = SerializerContent.builder()
				.receiver(sender)
				.message(message)
				.build();
		sender.sendMessage(Serializer.serialize(content));
	}

	/**
	 * Gives the credential the account a registering player would get: an Identica account, its Credential
	 * link and the provider profile. Without them the player would join as unregistered despite the password.
	 * An existing account of that username receives the link; it is primary only when the account has none.
	 */
	private boolean ensureAccount(@NotNull String username, @NotNull String providerSubject) {
		String providerId = CredentialConstants.PROVIDER_ID;
		if (providerLinkPersistenceService.findBySubject(providerId, providerSubject).isPresent())
			return true;

		long now = System.currentTimeMillis();
		Account account = accountService.find(username).stream().findFirst().orElse(null);
		if (account == null) {
			UUID uniqueId = uniqueIdGenerator.resolveConfiguredUniqueId(username, providerSubject, null);
			if (uniqueId == null) return false;

			account = accountService.create(Account.builder()
					.uniqueId(uniqueId)
					.username(username)
					.createdAt(now)
					.lastSeenAt(now)
					.build());
		}

		providerLinkPersistenceService.upsert(AccountProviderLink.builder()
				.uniqueId(account.getUniqueId())
				.providerId(providerId)
				.providerSubject(providerSubject)
				.primaryLink(providerLinkPersistenceService.findByUniqueId(account.getUniqueId()).isEmpty())
				.linkedAt(now)
				.lastSeenAt(now)
				.build());
		providerProfilePersistenceService.upsert(AccountProviderProfile.builder()
				.providerId(providerId)
				.providerSubject(providerSubject)
				.providerUsername(username)
				.build());

		return true;
	}

	private String resolveProviderSubject(String username) {
		if (username == null) return null;
		UUID uuid = UniqueIdGenerator.offlinePlayerUniqueId(username);
		return uuid != null ? uuid.toString() : null;
	}
}
