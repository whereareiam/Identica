package me.whereareiam.identica.addon.dialogs.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * When to show which dialog. Rules are tried in order; the first whose trigger and conditions all match decides.
 */
@Value
@Builder
public class DialogRule {
	/**
	 * ID of the trigger the rule reacts to, such as {@code step-waiting}.
	 */
	@NotNull String on;
	/**
	 * Conditions by attribute name; each matches when the trigger's attribute has its value, ignoring case.
	 */
	@NotNull @Singular("when") Map<String, String> when;
	/**
	 * ID of the dialog to show, or {@code null} to show none, which keeps the player in chat for this moment.
	 */
	@Nullable String dialog;
}
