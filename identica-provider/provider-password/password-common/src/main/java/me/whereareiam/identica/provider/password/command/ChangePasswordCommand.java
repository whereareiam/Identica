package me.whereareiam.identica.provider.password.command;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.provider.password.PasswordConstants;
import me.whereareiam.identica.provider.password.account.PasswordAccountService;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.config.PasswordSettings;
import me.whereareiam.identica.provider.password.cryptography.CryptographyService;
import me.whereareiam.identica.provider.password.cryptography.PasswordCandidate;
import me.whereareiam.identica.provider.password.model.PasswordAccount;
import me.whereareiam.identica.provider.password.util.PasswordRules;
import me.whereareiam.identica.provider.password.type.PasswordChangeReason;
import me.whereareiam.identica.util.UniqueIdGenerator;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ChangePasswordCommand {
	private final Provider<PasswordMessages> messagesProvider;
	private final Provider<PasswordSettings> settingsProvider;
	private final SessionService sessionService;
	private final PasswordAccountService accountService;
	private final CryptographyService cryptographyService;
	private final PasswordRules passwordPolicy;

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

		PasswordMessages.ChangePassword messages = messagesProvider.get().getChangePassword();
		Session session = sessionService.findByUniqueId(identity.getUniqueId()).join().orElse(null);
		if (session == null) {
			sendMessage(identity, messages.getNotLoggedIn());
			return;
		}

		if (next == null || isRepeatMismatch(next, repeat)) {
			sendMessage(identity, messages.getMismatch());
			return;
		}

		String error = passwordPolicy.validate(next);
		if (error != null && !error.isBlank()) {
			sendMessage(identity, error);
			return;
		}

		String providerSubject = null;
		if (session.getProviderId() != null
				&& session.getProviderId().equalsIgnoreCase(PasswordConstants.PROVIDER_ID)) {
			providerSubject = session.getProviderSubject();
		}
		if (providerSubject == null || providerSubject.isBlank())
			providerSubject = resolveProviderSubject(identity.getUsername());
		PasswordAccount account = providerSubject != null
				? accountService.find(providerSubject).orElse(null)
				: null;
		if (account == null) {
			sendMessage(identity, messagesProvider.get().getScenario().getAuthentication().getStatus().getNotRegistered());
			return;
		}

		if (!cryptographyService.verify(account, current)) {
			sendMessage(identity, messages.getInvalidCurrent());
			return;
		}

		PasswordCandidate candidate = cryptographyService.hash(next);
		if (candidate != null) {
			accountService.updatePassword(
					account,
					candidate.getPasswordHash(),
					candidate.getHashingMethod(),
					PasswordChangeReason.CHANGE
			);
		}
		sendMessage(identity, messages.getSuccess());
	}

	private boolean isRepeatMismatch(@NotNull String next, String repeat) {
		if (repeat != null && !repeat.isBlank())
			return !next.equals(repeat);

		return requiresRepeat();
	}

	private boolean requiresRepeat() {
		PasswordSettings settings = settingsProvider.get();
		if (settings == null || settings.getScenario() == null)
			return true;

		PasswordSettings.Scenario.ChangePassword changePassword = settings.getScenario().getChangePassword();
		return changePassword == null || changePassword.isRequireRepeat();
	}

	private void sendMessage(@NotNull Identity identity, String message) {
		if (message == null || message.isBlank())
			return;
		SerializerContent content = SerializerContent.builder()
				.receiver(identity)
				.message(message)
				.build();
		identity.sendMessage(Serializer.serialize(content));
	}

	private String resolveProviderSubject(String username) {
		if (username == null) return null;
		UUID uuid = UniqueIdGenerator.offlinePlayerUniqueId(username);
		return uuid != null ? uuid.toString() : null;
	}
}
