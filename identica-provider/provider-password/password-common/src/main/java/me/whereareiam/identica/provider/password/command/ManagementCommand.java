package me.whereareiam.identica.provider.password.command;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.provider.password.model.PasswordAccount;
import me.whereareiam.identica.provider.password.type.PasswordChangeReason;
import me.whereareiam.identica.provider.password.account.PasswordAccountService;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.cryptography.CryptographyService;
import me.whereareiam.identica.provider.password.cryptography.PasswordCandidate;
import me.whereareiam.identica.provider.password.util.PasswordRules;
import me.whereareiam.identica.util.UniqueIdGenerator;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ManagementCommand {
	private final Provider<PasswordMessages> messagesProvider;
	private final PasswordAccountService accountService;
	private final CryptographyService cryptographyService;
	private final PasswordRules passwordPolicy;

	@Definition("admin-force-register")
	@Command("identica password register <username> <password>")
	public void forceRegister(
			@NotNull Actor sender,
			@Argument("username") String username,
			@Argument(value = "password", parser = "password") String password
	) {
		PasswordMessages.Commands.Admin messages = messagesProvider.get().getCommands().getAdmin();
		String providerSubject = resolveProviderSubject(username);
		if (providerSubject == null) {
			sendMessage(sender, messages.getNotFound());
			return;
		}

		if (accountService.find(providerSubject).isPresent()) {
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

		PasswordAccount account = accountService.register(
				providerSubject,
				candidate.getPasswordHash(),
				candidate.getHashingMethod(),
				PasswordChangeReason.ADMIN_SET
		).orElse(null);
		if (account == null) {
			sendMessage(sender, messages.getNotFound());
			return;
		}

		sendMessage(sender, messages.getRegistered());
	}

	@Definition("admin-set-password")
	@Command("identica password setpassword <username> <password>")
	public void setPassword(
			@NotNull Actor sender,
			@Argument("username") String username,
			@Argument(value = "password", parser = "password") String password
	) {
		PasswordMessages.Commands.Admin messages = messagesProvider.get().getCommands().getAdmin();
		String providerSubject = resolveProviderSubject(username);
		if (providerSubject == null) {
			sendMessage(sender, messages.getNotFound());
			return;
		}

		PasswordAccount account = accountService.find(providerSubject).orElse(null);
		if (account == null) {
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
			accountService.updatePassword(
					account,
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

	private String resolveProviderSubject(String username) {
		if (username == null) return null;
		UUID uuid = UniqueIdGenerator.offlinePlayerUniqueId(username);
		return uuid != null ? uuid.toString() : null;
	}
}
