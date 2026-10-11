package me.whereareiam.identica.platform.common.stage.login;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * Finds a refusal that a prepare stage stored for a connection, and renders the message every prepare refusal is
 * shown with.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PreparedRefusal {
	private final @NotNull PrepareStateStore prepareStateStore;
	private final @NotNull Provider<Messages> messagesProvider;

	/**
	 * Returns the refusal stored for a connection and forgets it. The decision is read by the connection's unique ID
	 * first and by its connection key when the unique ID has none.
	 *
	 * @param connectionUniqueId unique ID the connection logs in with
	 * @param connectionKey      connection key built from the username the profile stage saw
	 * @return reason the connection is refused with, or empty when no prepare stage refused it
	 */
	public @NotNull Optional<Component> find(@NotNull UUID connectionUniqueId, @Nullable String connectionKey) {
		PrepareDecision prepared = prepareStateStore.peek(connectionUniqueId)
				.or(() -> hasText(connectionKey) ? prepareStateStore.peek(connectionKey) : Optional.empty())
				.orElse(null);
		if (prepared == null || !prepared.isDenied()) return Optional.empty();

		prepareStateStore.clearIf(connectionUniqueId, connectionKey, PrepareDecision::isDenied);
		return Optional.of(message(prepared));
	}

	/**
	 * Renders the reason a denied prepare decision is shown with: its own message, or the prepare denial message
	 * when it carries no text.
	 *
	 * @param decision denied decision
	 * @return reason shown to the player
	 */
	public @NotNull Component message(@NotNull PrepareDecision decision) {
		return message(decision.getDenialMessage());
	}

	/**
	 * Renders the reason a prepare refusal is shown with: the given message, or the prepare denial message when it
	 * has no text.
	 *
	 * @param denialMessage message the refusal carries, if any
	 * @return reason shown to the player
	 */
	public @NotNull Component message(@Nullable String denialMessage) {
		String message = hasText(denialMessage)
				? denialMessage
				: String.join("\n", messagesProvider.get().getEngine().getPrepare().getHandshakeDenied());

		return Serializer.serialize(message);
	}

	private static boolean hasText(@Nullable String value) {
		return value != null && !value.isBlank();
	}
}
