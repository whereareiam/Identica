package me.whereareiam.identica.provider.credential.command;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.AvailableWhilePending;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.provider.credential.CredentialMessageDefinitions;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.pipeline.CredentialRegistrationAttempt;
import me.whereareiam.keystone.Actor;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PassCommand {
	private final ConnectionCoordinator connectionCoordinator;
	private final Provider<CredentialMessages> messagesProvider;
	private final MessageService messageService;

	@AvailableWhilePending
	@Definition("pass")
	@Command("pass <password>")
	public void pass(@NotNull Actor sender, @Argument(value = "password", parser = "password") String password) {
		if (!(sender instanceof Identity identity)) return;

		handleDecision(identity, advance(identity, new CredentialRegistrationAttempt(password, false)));
	}

	@AvailableWhilePending
	@Definition("passconfirm")
	@Command("passconfirm <password>")
	public void passConfirm(@NotNull Actor sender, @Argument(value = "password", parser = "password") String repeat) {
		if (!(sender instanceof Identity identity))
			return;

		handleDecision(identity, advance(identity, new CredentialRegistrationAttempt(repeat, true)));
	}

	private void handleDecision(@NotNull Identity identity, ConnectionDecision decision) {
		if (decision == null || decision.getStatus() == null)
			return;

		switch (decision.getStatus()) {
			case WAIT -> sendMessage(identity, decision.getMessage());
			case DENY, REQUIRE_RECONNECT -> disconnect(identity, decision.getMessage());
			case NO_PENDING -> sendMessage(identity, noPendingMessage());
			default -> {
			}
		}
	}

	/**
	 * Continues the pending journey with the password the player typed. The pipeline looks for the pending journey
	 * in the connection's turn, after every earlier run has finished, so a password typed before its prompt waits
	 * for the run that shows the prompt instead of being turned away. The pipeline puts the attempt into the state
	 * of the run it starts.
	 */
	private ConnectionDecision advance(@NotNull Identity identity, @NotNull CredentialRegistrationAttempt attempt) {
		AdvanceRequest request = AdvanceRequest.builder()
				.connectionUniqueId(identity.getConnectionUniqueId())
				.identity(identity)
				.input(state -> state.put(CredentialRegistrationAttempt.KEY, attempt))
				.build();
		return connectionCoordinator.advance(request).toCompletableFuture().join();
	}

	private @NotNull PlayerMessage noPendingMessage() {
		return CredentialMessageDefinitions.NO_NEW_PASSWORD.create(messagesProvider.get());
	}

	private void sendMessage(@NotNull Identity identity, @Nullable PlayerMessage message) {
		if (message == null || message.isEmpty()) return;

		messageService.send(identity, message);
	}

	/** A denial disconnects whether or not it comes with a reason to show. */
	private void disconnect(@NotNull Identity identity, @Nullable PlayerMessage message) {
		identity.disconnect(message != null ? messageService.disconnectReason(identity, message) : Component.empty());
	}
}
