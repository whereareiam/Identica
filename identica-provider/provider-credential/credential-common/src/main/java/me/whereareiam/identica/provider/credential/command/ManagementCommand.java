package me.whereareiam.identica.provider.credential.command;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.persistence.AccountReservationPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.username.UsernameWriteResult;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.provider.AccountProviderProfile;
import me.whereareiam.identica.provider.credential.CredentialConstants;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.cryptography.PasswordCandidate;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.resolver.CredentialSubjectResolver;
import me.whereareiam.identica.provider.credential.type.PasswordChangeReason;
import me.whereareiam.identica.provider.credential.util.PasswordRules;
import me.whereareiam.identica.util.UniqueIdGenerator;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
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
	private final AccountReservationPersistenceService accountReservationPersistenceService;
	private final UniqueIdGenerator uniqueIdGenerator;
	private final CredentialSubjectResolver subjectResolver;

	/**
	 * Registers a Credential account for a player who has not joined yet. Only a new account is registered: a
	 * username an account holds in any case, through any provider, is refused and the account is named.
	 */
	@Definition("admin-force-register")
	@Command("identica credential register <username> <password>")
	public void forceRegister(
			@NotNull Actor sender,
			@Argument("username") String username,
			@Argument(value = "password", parser = "password") String password
	) {
		CredentialMessages.Commands.Admin messages = messagesProvider.get().getCommands().getAdmin();
		String providerSubject = offlineSubject(username);
		if (providerSubject == null) {
			sendMessage(sender, messages.getNotFound());
			return;
		}

		Account existing = findExistingAccount(username, providerSubject);
		if (existing != null) {
			sendMessage(sender, messages.getAccountExists(), accountPlaceholders(username, existing));
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

		Account account = createAccount(sender, username, providerSubject);
		if (account == null) return;

		linkCredential(account, username, providerSubject);
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
		String providerSubject = subjectResolver.resolve(username);
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
		sendMessage(sender, message, Map.of());
	}

	private void sendMessage(@NotNull Actor sender, @NotNull String message, @NotNull Map<String, String> placeholders) {
		if (message.isBlank()) return;
		SerializerContent content = SerializerContent.builder()
				.receiver(sender)
				.message(message)
				.placeholders(placeholders)
				.build();
		sender.sendMessage(Serializer.serialize(content));
	}

	/**
	 * Returns the account the username or its Credential subject belongs to already: the account linked to the
	 * subject, or else an account holding the username in any case, whichever provider it uses.
	 */
	private @Nullable Account findExistingAccount(@NotNull String username, @NotNull String providerSubject) {
		Account linked = providerLinkPersistenceService.findBySubject(CredentialConstants.PROVIDER_ID, providerSubject)
				.flatMap(link -> accountService.find(link.getUniqueId()))
				.orElse(null);
		if (linked != null) return linked;

		return accountService.find(username).stream().findFirst().orElse(null);
	}

	/**
	 * Creates the account a registering player would get. It takes the UUID reserved for its username, as a
	 * registering player's account does, or else the one {@code uniqueIdMode} assigns; {@code PREMIUM} always
	 * assigns its own. A refusal is told to the sender, and nothing is created.
	 */
	private @Nullable Account createAccount(@NotNull Actor sender, @NotNull String username, @NotNull String providerSubject) {
		CredentialMessages.Commands.Admin messages = messagesProvider.get().getCommands().getAdmin();
		UUID uniqueId = resolveNewAccountId(username, providerSubject);
		if (uniqueId == null) {
			sendMessage(sender, messages.getNotFound());
			return null;
		}

		Account holder = accountService.find(uniqueId).orElse(null);
		if (holder != null) {
			sendMessage(sender, messages.getReservationTaken(), accountPlaceholders(username, holder));
			return null;
		}

		long now = System.currentTimeMillis();
		Account account = Account.builder()
				.uniqueId(uniqueId)
				.username(username)
				.createdAt(now)
				.lastSeenAt(now)
				.build();
		UsernameWriteResult result = accountService.create(account, AccountUsernameSource.PROVIDER, CredentialConstants.PROVIDER_ID);
		if (!result.isWritten()) {
			refuseTakenUsername(sender, username, result.getHolderUniqueId());
			return null;
		}

		deleteReservation(username);

		return account;
	}

	/**
	 * Names the account that took the username between the check and the creation.
	 */
	private void refuseTakenUsername(@NotNull Actor sender, @NotNull String username, @Nullable UUID holderUniqueId) {
		CredentialMessages.Commands.Admin messages = messagesProvider.get().getCommands().getAdmin();
		Account holder = holderUniqueId != null ? accountService.find(holderUniqueId).orElse(null) : null;
		if (holder == null) {
			sendMessage(sender, messages.getNotFound());
			return;
		}

		sendMessage(sender, messages.getAccountExists(), accountPlaceholders(username, holder));
	}

	/**
	 * Gives a new account its Credential link and provider profile. Without them the player would join as
	 * unregistered despite the password.
	 */
	private void linkCredential(@NotNull Account account, @NotNull String username, @NotNull String providerSubject) {
		long now = System.currentTimeMillis();
		providerLinkPersistenceService.upsert(AccountProviderLink.builder()
				.uniqueId(account.getUniqueId())
				.providerId(CredentialConstants.PROVIDER_ID)
				.providerSubject(providerSubject)
				.primaryLink(true)
				.linkedAt(now)
				.lastSeenAt(now)
				.build());
		providerProfilePersistenceService.upsert(AccountProviderProfile.builder()
				.providerId(CredentialConstants.PROVIDER_ID)
				.providerSubject(providerSubject)
				.providerUsername(username)
				.build());
	}

	private @Nullable UUID resolveNewAccountId(@NotNull String username, @NotNull String providerSubject) {
		UUID reserved = uniqueIdGenerator.requiresConfiguredUniqueId() ? null : findReservedId(username);
		if (reserved != null) return reserved;

		return uniqueIdGenerator.resolveConfiguredUniqueId(username, providerSubject, null);
	}

	private @Nullable UUID findReservedId(@NotNull String username) {
		String reservationKey = AccountReservationPersistenceService.usernameKey(username);
		if (reservationKey == null) return null;

		return accountReservationPersistenceService.find(reservationKey).orElse(null);
	}

	private void deleteReservation(@NotNull String username) {
		String reservationKey = AccountReservationPersistenceService.usernameKey(username);
		if (reservationKey == null) return;

		accountReservationPersistenceService.delete(reservationKey);
	}

	private static @NotNull Map<String, String> accountPlaceholders(@NotNull String target, @NotNull Account account) {
		return Map.of(
				"target", target,
				"username", account.getUsername(),
				"uniqueId", account.getUniqueId().toString()
		);
	}

	private static @Nullable String offlineSubject(@Nullable String username) {
		if (username == null) return null;

		UUID uuid = UniqueIdGenerator.offlinePlayerUniqueId(username);
		return uuid != null ? uuid.toString() : null;
	}
}
