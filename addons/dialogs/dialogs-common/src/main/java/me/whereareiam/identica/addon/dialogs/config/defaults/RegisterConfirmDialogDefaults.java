package me.whereareiam.identica.addon.dialogs.config.defaults;

import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.addon.dialogs.config.DialogDocument;
import me.whereareiam.identica.addon.dialogs.type.ActionType;

import java.util.List;

/**
 * The shipped {@code register-confirm} dialog, for Credential's steps that wait for {@code /passconfirm}.
 */
public class RegisterConfirmDialogDefaults implements DefaultsProvider<DialogDocument> {
	@Override
	public DialogDocument supply(DialogDocument dialog) {
		return DialogParts.form(
				dialog,
				List.of(
						"<white>Repeat your password to finish the registration.",
						DialogParts.MESSAGE
				),
				DialogParts.text("password", "<white>Password", 64),
				DialogParts.action("<green>Register", ActionType.COMMAND, "passconfirm $(password)")
		);
	}
}
