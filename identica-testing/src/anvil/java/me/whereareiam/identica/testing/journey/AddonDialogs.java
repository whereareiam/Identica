package me.whereareiam.identica.testing.journey;

import lombok.RequiredArgsConstructor;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.addon.dialogs.config.DialogDocument;
import me.whereareiam.identica.addon.dialogs.model.Dialog;

import java.nio.file.Path;

/**
 * The dialogs a running proxy's dialogs addon uses, read from the files it generated into the addon's own models.
 * A journey names a dialog by its ID and its buttons through these models, so it follows the configured text.
 */
@RequiredArgsConstructor
final class AddonDialogs {
	private static final Configura CONFIGURA = Config.builder().format(Format.YAML).build();

	private final Path dataDirectory;

	/**
	 * Returns a configured dialog.
	 *
	 * @param id the dialog's ID, the name of its file in {@code dialogs/}
	 */
	Dialog dialog(String id) {
		return CONFIGURA.read(dataDirectory.resolve("dialogs").resolve(id + ".yml"), DialogDocument.class).toDialog(id);
	}
}
