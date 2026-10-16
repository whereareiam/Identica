package me.whereareiam.identica.addon.dialogs.config.defaults;

import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.addon.dialogs.config.DialogDocument;

import java.util.List;

/**
 * The shipped {@code verification} dialog, for the steps that wait for a two-factor code with {@code /2fa confirm}.
 */
public class VerificationDialogDefaults implements DefaultsProvider<DialogDocument> {
	@Override
	public DialogDocument supply(DialogDocument dialog) {
		return DialogParts.form(
				dialog,
				List.of(
						"<white>Enter the code from your authenticator app, or a recovery code.",
						DialogParts.MESSAGE
				),
				DialogParts.text("code", "<white>Code", 32),
				DialogParts.command("<green>Confirm", "verification-confirm", "$(code)")
		);
	}
}
