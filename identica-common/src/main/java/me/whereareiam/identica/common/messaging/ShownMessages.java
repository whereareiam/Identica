package me.whereareiam.identica.common.messaging;

import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.message.MessagePart;
import me.whereareiam.identica.model.message.PartRendering;
import me.whereareiam.identica.model.message.PlayerMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What one connection is shown: its open prompt and its last notice, each with the parts that stay until they are
 * taken down, and the parts that wait until the player can see them. Callers synchronize on it.
 */
final class ShownMessages {
	private final UUID connectionUniqueId;
	private final List<Waiting> waiting = new ArrayList<>();
	private @Nullable Shown prompt;
	private @Nullable Shown notice;

	ShownMessages(@NotNull UUID connectionUniqueId) {
		this.connectionUniqueId = connectionUniqueId;
	}

	/**
	 * Makes a message the current one of its kind, a prompt or a notice, and takes down what the previous one of that
	 * kind showed. A new prompt also drops what still waited of earlier prompts the player already saw another way.
	 *
	 * @param shown the message and what it showed
	 * @param waits its parts that wait until the player can see them
	 */
	void current(@NotNull Shown shown, @NotNull List<MessagePart> waits) {
		boolean isPrompt = shown.isPrompt();
		Shown previous = isPrompt ? prompt : notice;
		if (isPrompt) {
			prompt = shown;
			dropSeenPrompts();
		} else {
			notice = shown;
		}

		for (MessagePart part : waits)
			waiting.add(new Waiting(shown, part));

		if (previous != null) previous.close(connectionUniqueId);
	}

	/**
	 * Returns the open prompt.
	 *
	 * @return the open prompt, or {@code null} when none is open
	 */
	@Nullable Shown prompt() {
		return prompt;
	}

	/**
	 * Ends the open prompt: takes down what it showed and drops what still waits of prompts the player already saw
	 * another way.
	 */
	void endPrompt() {
		Shown ended = prompt;
		prompt = null;
		dropSeenPrompts();
		if (ended != null) ended.close(connectionUniqueId);
	}

	/**
	 * Drops what waits of prompts that are no longer open and that the player saw another way, such as in a dialog on
	 * the loading screen; what waits of a prompt the player saw nowhere stays, so they still learn what it said.
	 */
	private void dropSeenPrompts() {
		waiting.removeIf(entry -> entry.shown() != prompt && entry.shown().isPrompt() && !entry.shown().parts().isEmpty());
	}

	/**
	 * Tells whether a message is still current: the open prompt, or the last notice.
	 *
	 * @param shown the message
	 * @return {@code true} when it is current
	 */
	boolean isCurrent(@NotNull Shown shown) {
		return shown == prompt || shown == notice;
	}

	/**
	 * Takes out the parts that wait, in the order they were sent.
	 *
	 * @return the waiting parts
	 */
	@NotNull List<Waiting> takeWaiting() {
		List<Waiting> taken = List.copyOf(waiting);
		waiting.clear();
		return taken;
	}

	/**
	 * Puts a part back to wait, such as one the player still cannot see.
	 *
	 * @param entry the waiting part
	 */
	void waitAgain(@NotNull Waiting entry) {
		waiting.add(entry);
	}

	/**
	 * Takes down everything shown and drops what waits.
	 */
	void close() {
		Shown openPrompt = prompt;
		Shown lastNotice = notice;
		prompt = null;
		notice = null;
		waiting.clear();
		if (openPrompt != null) openPrompt.close(connectionUniqueId);
		if (lastNotice != null) lastNotice.close(connectionUniqueId);
	}

	/**
	 * A message shown to the connection, with the parts it showed by type and what takes the lasting ones down.
	 *
	 * @param message the message
	 * @param parts the parts it showed, by part type
	 * @param closes what takes its lasting parts down, by part type
	 */
	record Shown(
			@NotNull PlayerMessage message,
			@NotNull Map<String, MessagePart> parts,
			@NotNull Map<String, PartRendering.Close> closes
	) {
		Shown(@NotNull PlayerMessage message) {
			this(message, new LinkedHashMap<>(), new LinkedHashMap<>());
		}

		boolean isPrompt() {
			return message.getPrompt() != null;
		}

		/**
		 * Records that a part of the message is shown, replacing what took down an earlier rendering of its type.
		 *
		 * @param type the part type
		 * @param part the part
		 * @param close what takes it down, or {@code null} for a part that cannot be taken back
		 */
		void shown(@NotNull String type, @NotNull MessagePart part, @Nullable PartRendering.Close close) {
			parts.put(type, part);
			if (close == null) closes.remove(type);
			else closes.put(type, close);
		}

		/**
		 * Takes down every lasting part of the message.
		 *
		 * @param connectionUniqueId the connection, for the log
		 */
		void close(@NotNull UUID connectionUniqueId) {
			for (PartRendering.Close close : List.copyOf(closes.values())) {
				try {
					close.close();
				} catch (RuntimeException failure) {
					Logger.severe(failure, "Could not take down a message part for connection %s", connectionUniqueId);
				}
			}
			closes.clear();
		}
	}

	/**
	 * A part of a message that waits until the player can see it.
	 *
	 * @param shown the message it belongs to
	 * @param part the part
	 */
	record Waiting(@NotNull Shown shown, @NotNull MessagePart part) {
	}
}
