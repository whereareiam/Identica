package me.whereareiam.identica.addon.dialogs.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import me.whereareiam.identica.addon.dialogs.type.InputType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * One input of a dialog. Fields that do not belong to the input's type are ignored.
 */
@Value
@Builder
public class DialogInput {
	/**
	 * Key a command names the input's value by, as {@code $(key)}.
	 */
	@NotNull String key;
	@NotNull InputType type;
	/**
	 * MiniMessage label shown with the input.
	 */
	@NotNull String label;
	/**
	 * Text a text input starts with.
	 */
	@NotNull @Builder.Default String initial = "";
	/**
	 * Longest text a text input accepts.
	 */
	@Builder.Default int maxLength = 32;
	/**
	 * Whether a checkbox starts checked.
	 */
	boolean checked;
	/**
	 * Text a checked checkbox puts into a command.
	 */
	@NotNull @Builder.Default String onTrue = "true";
	/**
	 * Text an unchecked checkbox puts into a command.
	 */
	@NotNull @Builder.Default String onFalse = "false";
	/**
	 * Options of a single-option input, in display order.
	 */
	@NotNull @Singular List<DialogOption> options;
	/**
	 * Lowest value of a slider.
	 */
	float start;
	/**
	 * Highest value of a slider.
	 */
	float end;
	/**
	 * Step between a slider's values, or {@code null} when it moves freely.
	 */
	@Nullable Float step;
}
