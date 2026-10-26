package me.whereareiam.identica.addon.dialogs.config;

import me.whereareiam.configura.Configura;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.addon.dialogs.config.defaults.LoginDialogDefaults;
import me.whereareiam.identica.addon.dialogs.config.defaults.RegisterConfirmDialogDefaults;
import me.whereareiam.identica.addon.dialogs.config.defaults.RegisterDialogDefaults;
import me.whereareiam.identica.addon.dialogs.config.defaults.RulesDefaults;
import me.whereareiam.identica.addon.dialogs.config.defaults.VerificationDialogDefaults;
import me.whereareiam.identica.addon.dialogs.model.Dialog;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The addon's files in its data folder: {@code rules} and one file per dialog in {@code dialogs/}. The shipped
 * dialogs are written once, when {@code dialogs/} does not exist yet, so a dialog an administrator deleted stays
 * deleted. Reloading Identica reads the files again.
 */
public final class DialogsFiles implements Reloadable {
	private static final Map<String, Class<? extends DefaultsProvider<DialogDocument>>> SHIPPED = Map.of(
			"login", LoginDialogDefaults.class,
			"register", RegisterDialogDefaults.class,
			"register-confirm", RegisterConfirmDialogDefaults.class,
			"verification", VerificationDialogDefaults.class
	);

	private final Configura configura;
	private final Path folder;
	private final Consumer<List<DialogRule>> rulesRead;
	private volatile List<DialogRule> rules = List.of();
	private volatile Map<String, Dialog> dialogs = Map.of();

	/**
	 * Reads the files, writing the shipped ones on the first start.
	 *
	 * @param configura Identica's configuration reader and writer
	 * @param folder the addon's data folder
	 * @param rulesRead told the rules each time the files are read
	 */
	public DialogsFiles(@NotNull Configura configura, @NotNull Path folder, @NotNull Consumer<List<DialogRule>> rulesRead) {
		this.configura = configura;
		this.folder = folder;
		this.rulesRead = rulesRead;
		reload();
	}

	@Override
	public void reload() {
		Path dialogsFolder = folder.resolve("dialogs");
		if (Files.notExists(dialogsFolder)) writeShipped(dialogsFolder);

		rules = configura.withDefaults(RulesDefaults.class).update(folder.resolve("rules"), RulesDocument.class).toRules();
		dialogs = readDialogs(dialogsFolder);
		rulesRead.accept(rules);
	}

	public @NotNull List<DialogRule> rules() {
		return rules;
	}

	public @NotNull Map<String, Dialog> dialogs() {
		return dialogs;
	}

	private void writeShipped(Path dialogsFolder) {
		createDirectories(dialogsFolder);
		SHIPPED.forEach((id, defaults) -> configura.withDefaults(defaults).update(dialogsFolder.resolve(id), DialogDocument.class));
	}

	private Map<String, Dialog> readDialogs(Path dialogsFolder) {
		Map<String, Dialog> read = new LinkedHashMap<>();
		try (Stream<Path> files = Files.list(dialogsFolder)) {
			for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
				String id = withoutExtension(file.getFileName().toString());
				read.put(id, configura.update(dialogsFolder.resolve(id), DialogDocument.class).toDialog(id));
			}
		} catch (IOException failure) {
			throw new UncheckedIOException("Could not read the dialogs in " + dialogsFolder, failure);
		}

		return Map.copyOf(read);
	}

	private static String withoutExtension(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? name : name.substring(0, dot);
	}

	private static void createDirectories(Path directory) {
		try {
			Files.createDirectories(directory);
		} catch (IOException failure) {
			throw new UncheckedIOException("Could not create " + directory, failure);
		}
	}
}
