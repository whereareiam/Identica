package me.whereareiam.identica.adapter.command.executor.admin;

import com.google.inject.Inject;
import com.google.inject.Provider;
import me.whereareiam.identica.adapter.command.executor.admin.base.ConfirmableAdminCommand;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.annotation.Suggestions;
import me.whereareiam.identica.adapter.command.suggestion.CrossPlayerSuggestions;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.AccountOperationRequest;
import me.whereareiam.keystone.Actor;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class ClearCommand extends ConfirmableAdminCommand<Account> {
	private final Provider<Messages> messagesProvider;

	@Inject
	public ClearCommand(
			@NotNull Provider<Commands> commandsProvider,
			@NotNull Provider<Messages> messagesProvider,
			@NotNull AccountService accountService
	) {
		super(commandsProvider, accountService);
		this.messagesProvider = messagesProvider;
	}

	@Definition("admin-clear")
	@Command("identica admin clear <target>")
	public void clear(
			@NotNull Actor sender,
			@Argument("target") @Suggestions(CrossPlayerSuggestions.KEY) String target
	) {
		Messages.Commands.Admin.Clear messages = messages();
		Account resolved = resolveTarget(
				sender,
				target,
				messages.getNotFound(),
				messages.getMultiple(),
				"identica admin clear"
		);
		if (resolved == null) return;

		request(sender, resolved);
		sendConfirm(sender, target, resolved, messages);
	}

	@Definition("admin-clear-confirm")
	@Command("identica admin clear confirm")
	public void confirmClear(@NotNull Actor sender) {
		Messages.Commands.Admin.Clear messages = messages();
		Confirmation<Account> confirmation = confirm(sender);
		if (confirmation.status() == ConfirmationStatus.NO_PENDING) {
			sendMessage(sender, messages.getNoPending(), Map.of());
			return;
		}
		if (confirmation.status() == ConfirmationStatus.EXPIRED) {
			sendMessage(sender, messages.getExpired(), Map.of());
			return;
		}

		Account account = confirmation.value();
		if (account == null) return;

		try {
			accountService().clear(AccountOperationRequest.builder()
					.account(account)
					.disconnect(true)
					.disconnectMessage(String.join("\n", messages.getDisconnect()))
					.reason("admin-clear")
					.build());

			sendMessage(sender, messages.getSuccess(), Map.of(
					"scope", "clear",
					"uniqueId", account.getUniqueId().toString()
			));
		} catch (Exception e) {
			sendMessage(sender, messages.getError(), Map.of("error", String.valueOf(e.getMessage())));
		}
	}

	@Definition("admin-clear-cancel")
	@Command("identica admin clear cancel")
	public void cancelClear(@NotNull Actor sender) {
		Messages.Commands.Admin.Clear messages = messages();
		if (!cancel(sender)) {
			sendMessage(sender, messages.getNoPending(), Map.of());
			return;
		}

		sendMessage(sender, messages.getCancelled(), Map.of());
	}

	private void sendConfirm(
			@NotNull Actor sender,
			@NotNull String target,
			@NotNull Account account,
			@NotNull Messages.Commands.Admin.Clear messages
	) {
		sendMessage(sender, String.join("\n", messages.getConfirm()), Map.of(
				"target", target,
				"scope", "clear",
				"uniqueId", account.getUniqueId().toString()
		));
	}

	private Messages.Commands.Admin.Clear messages() {
		return messagesProvider.get().getCommands().getAdmin().getClear();
	}
}
