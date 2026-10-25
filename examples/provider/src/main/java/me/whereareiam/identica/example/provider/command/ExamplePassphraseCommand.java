package me.whereareiam.identica.example.provider.command;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.AvailableWhilePending;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.example.provider.ExampleMessageDefinitions;
import me.whereareiam.identica.example.provider.ExampleProvider;
import me.whereareiam.identica.example.provider.config.ExampleMessages;
import me.whereareiam.identica.example.provider.pipeline.ExamplePassphraseAttempt;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.keystone.Actor;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /passphrase <passphrase>}: hands the typed passphrase to the player's pending journey. It is marked
 * available while pending, so addons that hold back commands until a player has authenticated let it through.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ExamplePassphraseCommand {
	private final ConnectionCoordinator connectionCoordinator;
	private final Provider<ExampleMessages> messages;
	private final MessageService messageService;

	@AvailableWhilePending
	@Definition(ExampleProvider.PASSPHRASE_COMMAND)
	@Command("passphrase <passphrase>")
	public void passphrase(@NotNull Actor sender, @Argument(value = "passphrase", parser = "password") String passphrase) {
		if (!(sender instanceof Identity identity)) return;

		// The pipeline looks for the pending journey in the connection's turn, so a passphrase typed before its
		// prompt waits for the run that shows it. Without a pending journey the decision is NO_PENDING.
		AdvanceRequest request = AdvanceRequest.builder()
				.connectionUniqueId(identity.getConnectionUniqueId())
				.identity(identity)
				.input(state -> state.put(ExamplePassphraseAttempt.KEY, new ExamplePassphraseAttempt(passphrase)))
				.build();
		ConnectionDecision decision = connectionCoordinator.advance(request).toCompletableFuture().join();

		switch (decision.getStatus()) {
			case WAIT -> send(identity, decision.getMessage());
			case DENY, REQUIRE_RECONNECT -> identity.disconnect(decision.getMessage() != null
					? messageService.disconnectReason(identity, decision.getMessage())
					: Component.empty());
			case NO_PENDING -> send(identity, ExampleMessageDefinitions.NO_PENDING.create(messages.get()));
			default -> {
			}
		}
	}

	private void send(@NotNull Identity identity, @Nullable PlayerMessage message) {
		if (message == null || message.isEmpty()) return;

		messageService.send(identity, message);
	}
}
