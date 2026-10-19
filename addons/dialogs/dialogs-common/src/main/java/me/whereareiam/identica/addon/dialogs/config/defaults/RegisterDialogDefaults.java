package me.whereareiam.identica.addon.dialogs.config.defaults;

import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.addon.dialogs.config.DialogDocument;

/**
 * The shipped {@code register} dialog, for Credential's prompt for a new password, {@code credential:new-password}.
 */
public class RegisterDialogDefaults implements DefaultsProvider<DialogDocument> {
	@Override
	public DialogDocument supply(DialogDocument dialog) {
		return DialogParts.form(
				dialog,
				"Welcome!",
				"Create a password to protect your account.",
				DialogParts.text("password", "Password", 64),
				DialogParts.submit("Continue", "pass $(password)"),
				DialogParts.answers(
						"credential:passwords-differ", DialogParts.problem("The passwords did not match. Choose a password again."),
						"credential:no-new-password", DialogParts.problem("Choose a password first."),
						"credential:password-too-short", DialogParts.problem("This password is too short."),
						"credential:password-too-long", DialogParts.problem("This password is too long."),
						"credential:password-has-spaces", DialogParts.problem("A password cannot contain spaces."),
						"credential:password-missing-upper", DialogParts.problem("Add an uppercase letter."),
						"credential:password-missing-lower", DialogParts.problem("Add a lowercase letter."),
						"credential:password-missing-number", DialogParts.problem("Add a number."),
						"credential:password-missing-special", DialogParts.problem("Add a special character.")
				)
		);
	}
}
