package me.whereareiam.identica.addon.dialogs.config.defaults;

import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.addon.dialogs.config.DialogDocument;

/**
 * The shipped {@code register-confirm} dialog, for Credential's prompt to repeat the new password,
 * {@code credential:repeat-password}.
 */
public class RegisterConfirmDialogDefaults implements DefaultsProvider<DialogDocument> {
	@Override
	public DialogDocument supply(DialogDocument dialog) {
		return DialogParts.form(
				dialog,
				"Almost done!",
				"Repeat your password to finish the registration.",
				DialogParts.text("password", "Password", 64),
				DialogParts.submit("Register", "passconfirm $(password)"),
				DialogParts.answers()
		);
	}
}
