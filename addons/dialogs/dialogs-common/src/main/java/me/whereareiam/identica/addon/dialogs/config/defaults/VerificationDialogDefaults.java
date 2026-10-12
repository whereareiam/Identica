package me.whereareiam.identica.addon.dialogs.config.defaults;

import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.addon.dialogs.config.DialogDocument;

/**
 * The shipped {@code verification} dialog, for the prompt for a two-factor code, {@code verification:code}.
 */
public class VerificationDialogDefaults implements DefaultsProvider<DialogDocument> {
	@Override
	public DialogDocument supply(DialogDocument dialog) {
		return DialogParts.form(
				dialog,
				"One more step.",
				"Enter the code from your authenticator app, or a recovery code.",
				DialogParts.text("code", "Code", 32),
				DialogParts.submit("Confirm"),
				DialogParts.answers(
						"verification:code-invalid", DialogParts.problem("This code is not right. Try again."),
						"verification:code-no-challenge", DialogParts.problem("There is no code to confirm right now.")
				)
		);
	}
}
