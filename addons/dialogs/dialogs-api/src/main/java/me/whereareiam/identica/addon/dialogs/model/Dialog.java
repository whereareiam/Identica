package me.whereareiam.identica.addon.dialogs.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.identica.addon.dialogs.type.AfterAction;
import me.whereareiam.identica.addon.dialogs.type.DialogType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A configured dialog. Its texts are MiniMessage with {@code {placeholders}}, rendered for each player it is shown
 * to; a body line that renders empty is left out. {@code {answer}} holds the dialog's text for why Identica asks
 * again, from {@link #getAnswers()}.
 */
@Value
@Builder
public class Dialog {
	/**
	 * The dialog's ID, the name of its file without the extension.
	 */
	@NotNull String id;
	@NotNull DialogType type;
	@NotNull String title;
	/**
	 * Lines of text above the inputs.
	 */
	@NotNull @Singular("body") List<String> body;
	@NotNull @Singular List<DialogInput> inputs;
	/**
	 * Buttons in display order: the button of a notice, the confirming button of a confirmation, or every button of
	 * a multi-action dialog.
	 */
	@NotNull @Singular List<DialogAction> actions;
	/**
	 * The declining button of a confirmation, or the button below a multi-action dialog's buttons; the client
	 * presses it when the player closes the dialog with Escape.
	 */
	@Nullable DialogAction exitAction;
	/**
	 * Texts for {@code {answer}}, by the ID of why Identica asks again, such as {@code credential:password-invalid}.
	 * An answer the dialog has no text for leaves {@code {answer}} empty.
	 */
	@NotNull @Singular Map<String, String> answers;
	/**
	 * Whether the player may close the dialog with Escape.
	 */
	boolean closeWithEscape;
	@NotNull AfterAction afterAction;
}
