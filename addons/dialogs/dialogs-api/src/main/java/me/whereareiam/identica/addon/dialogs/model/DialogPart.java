package me.whereareiam.identica.addon.dialogs.model;

import me.whereareiam.identica.model.message.MessagePart;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The part of an Identica message that shows it as a dialog. The addon registers its type and presents every prompt
 * and message its rules name with a part that lets the rules pick the dialog; another plugin presents a message with
 * a dialog of its own choice:
 *
 * <pre>{@code
 * identica.getMessageService().present("example:passphrase", Presentation.builder()
 *         .part(DialogPart.of("passphrase"))
 *         .removed(ChatPart.TYPE)
 *         .build());
 * }</pre>
 *
 * <p>A player whose client cannot show dialogs, or who switched to chat, gets the message in chat instead.</p>
 *
 * @param dialog ID of the dialog to show, or {@code null} to let the rules pick it
 */
public record DialogPart(@Nullable String dialog) implements MessagePart {
	/**
	 * ID of the part type.
	 */
	public static final String TYPE = "dialog";

	/**
	 * Creates a part that shows the given dialog.
	 *
	 * @param dialog ID of the dialog, the name of its file without the extension
	 * @return the part
	 */
	public static @NotNull DialogPart of(@NotNull String dialog) {
		return new DialogPart(dialog);
	}

	/**
	 * Creates a part that shows the dialog the rules pick for the message and player, or none.
	 *
	 * @return the part
	 */
	public static @NotNull DialogPart byRules() {
		return new DialogPart(null);
	}

	@Override
	public @NotNull String type() {
		return TYPE;
	}
}
