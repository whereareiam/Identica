package me.whereareiam.identica.addon.dialogs.config.defaults;

import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.addon.dialogs.config.DialogDocument;

import java.util.List;

/**
 * The shipped {@code login} dialog, for Credential's password steps that wait for {@code /login}.
 */
public class LoginDialogDefaults implements DefaultsProvider<DialogDocument> {
	@Override
	public DialogDocument supply(DialogDocument dialog) {
		return DialogParts.form(
				dialog,
				List.of(
						"<white>Welcome back! Enter your password to log in.",
						DialogParts.MESSAGE
				),
				DialogParts.text("password", "<white>Password", 64),
				DialogParts.command("<green>Log in", "login", "$(password)")
		);
	}
}
