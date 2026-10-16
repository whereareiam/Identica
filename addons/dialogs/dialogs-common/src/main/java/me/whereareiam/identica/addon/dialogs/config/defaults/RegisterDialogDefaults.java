package me.whereareiam.identica.addon.dialogs.config.defaults;

import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.addon.dialogs.config.DialogDocument;

import java.util.List;

/**
 * The shipped {@code register} dialog, for Credential's steps that wait for {@code /pass}.
 */
public class RegisterDialogDefaults implements DefaultsProvider<DialogDocument> {
	@Override
	public DialogDocument supply(DialogDocument dialog) {
		return DialogParts.form(
				dialog,
				List.of(
						"<white>Create a password for your account.",
						DialogParts.MESSAGE
				),
				DialogParts.text("password", "<white>Password", 64),
				DialogParts.command("<green>Continue", "pass", "$(password)")
		);
	}
}
