package me.whereareiam.identica.addon.dialogs.config.defaults;

import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.addon.dialogs.config.DialogDocument;

/**
 * The shipped {@code login} dialog, for Credential's prompt for an account's password, {@code credential:password}.
 */
public class LoginDialogDefaults implements DefaultsProvider<DialogDocument> {
	@Override
	public DialogDocument supply(DialogDocument dialog) {
		return DialogParts.form(
				dialog,
				"Welcome back!",
				"Enter your password to continue to the server.",
				DialogParts.text("password", "Password", 64),
				DialogParts.submit("Log in"),
				DialogParts.answers(
						"credential:password-invalid", DialogParts.problem("Wrong password. Try again."),
						"credential:already-registered", DialogParts.problem("This name is already registered. Enter its password to log in.")
				)
		);
	}
}
