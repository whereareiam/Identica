package me.whereareiam.identica.example.provider.command;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.AvailableWhilePending;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.example.provider.ExampleProvider;
import me.whereareiam.identica.example.provider.config.ExampleMessages;
import me.whereareiam.identica.example.provider.pipeline.ExamplePassphraseAttempt;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.keystone.Actor;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /passphrase <passphrase>}: hands the typed passphrase to the player's pending journey. It is marked
 * available while pending, so addons that hold back commands until a player has authenticated let it through.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ExamplePassphraseCommand {
	private final ConnectionCoordinator connectionCoordinator;
	private final Provider<ExampleMessages> messages;

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
			case DENY, REQUIRE_RECONNECT -> identity.disconnect(Serializer.serialize(identity, decision.getMessage() == null ? "" : decision.getMessage()));
			case NO_PENDING -> send(identity, messages.get().getNoPending());
			default -> {
			}
		}
	}

	private static void send(@NotNull Identity identity, String message) {
		if (message == null || message.isBlank()) return;

		identity.sendMessage(Serializer.serialize(identity, message));
	}
}
