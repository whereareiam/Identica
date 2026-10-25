package me.whereareiam.identica.provider.credential.command;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.model.message.MessageDefinition;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.provider.credential.CredentialConstants;
import me.whereareiam.identica.provider.credential.CredentialMessageDefinitions;
import me.whereareiam.identica.provider.credential.account.CredentialAccountService;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyService;
import me.whereareiam.identica.provider.credential.cryptography.PasswordCandidate;
import me.whereareiam.identica.provider.credential.model.CredentialAccount;
import me.whereareiam.identica.provider.credential.type.PasswordChangeReason;
import me.whereareiam.identica.provider.credential.util.PasswordRules;
import me.whereareiam.identica.util.UniqueIdGenerator;
import me.whereareiam.keystone.Actor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PasswordChangeCommand {
	private final Provider<CredentialMessages> messagesProvider;
	private final Provider<CredentialSettings> settingsProvider;
	private final SessionService sessionService;
	private final CredentialAccountService credentialService;
	private final CryptographyService cryptographyService;
	private final PasswordRules passwordPolicy;
	private final MessageService messageService;

	@Definition("change-password")
	@Command("changepassword <current> <new> [repeat]")
	public void changePassword(
			@NotNull Actor sender,
			@Argument(value = "current", parser = "password") String current,
			@Argument(value = "new", parser = "password") String next,
			@Argument(value = "repeat", parser = "password") @Nullable String repeat
	) {
		if (!(sender instanceof Identity identity))
			return;

		CredentialMessages messages = messagesProvider.get();
		Session session = sessionService.findByConnection(identity.getAccountUniqueId(), SessionConnection.of(identity.getUniqueId())).join().orElse(null);
		if (session == null) {
			sendMessage(identity, CredentialMessageDefinitions.CHANGE_PASSWORD_NOT_LOGGED_IN.create(messages));
			return;
		}

		if (next == null || isRepeatMismatch(next, repeat)) {
			sendMessage(identity, CredentialMessageDefinitions.CHANGE_PASSWORD_MISMATCH.create(messages));
			return;
		}

		MessageDefinition<CredentialMessages> violation = passwordPolicy.check(next);
		if (violation != null) {
			sendMessage(identity, violation.create(messages));
			return;
		}

		String providerSubject = null;
		if (session.getProviderId() != null
				&& session.getProviderId().equalsIgnoreCase(CredentialConstants.PROVIDER_ID)) {
			providerSubject = session.getProviderSubject();
		}
		if (providerSubject == null || providerSubject.isBlank())
			providerSubject = resolveProviderSubject(identity.getUsername());
		CredentialAccount credential = providerSubject != null
				? credentialService.find(providerSubject).orElse(null)
				: null;
		if (credential == null) {
			sendMessage(identity, CredentialMessageDefinitions.NOT_REGISTERED.create(messages));
			return;
		}

		if (!cryptographyService.verify(credential, current)) {
			sendMessage(identity, CredentialMessageDefinitions.CHANGE_PASSWORD_INVALID_CURRENT.create(messages));
			return;
		}

		PasswordCandidate candidate = cryptographyService.hash(next);
		if (candidate != null) {
			credentialService.updatePassword(
					credential,
					candidate.getPasswordHash(),
					candidate.getHashingMethod(),
					PasswordChangeReason.CHANGE
			);
		}
		sendMessage(identity, CredentialMessageDefinitions.CHANGE_PASSWORD_SUCCESS.create(messages));
	}

	private boolean isRepeatMismatch(@NotNull String next, String repeat) {
		if (repeat != null && !repeat.isBlank())
			return !next.equals(repeat);

		return requiresRepeat();
	}

	private boolean requiresRepeat() {
		CredentialSettings settings = settingsProvider.get();
		if (settings == null || settings.getScenario() == null)
			return true;

		CredentialSettings.Scenario.ChangePassword changePassword = settings.getScenario().getChangePassword();
		return changePassword == null || changePassword.isRequireRepeat();
	}

	private void sendMessage(@NotNull Identity identity, @NotNull PlayerMessage message) {
		if (message.isEmpty()) return;

		messageService.send(identity, message);
	}

	private String resolveProviderSubject(String username) {
		if (username == null) return null;
		UUID uuid = UniqueIdGenerator.offlinePlayerUniqueId(username);
		return uuid != null ? uuid.toString() : null;
	}
}
