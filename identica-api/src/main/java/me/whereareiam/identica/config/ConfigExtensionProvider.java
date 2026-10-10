package me.whereareiam.identica.config;

import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;

/**
 * A view of a document that another {@link ConfigProvider} owns, such as a feature that adds its own section
 * to the entries of {@code providers/providers}.
 *
 * <p>The owner knows the document's defaults and writes them. An extension completes only what it adds, so it
 * is loaded and reloaded after every owner: loaded first, it would write the document without those defaults,
 * and the owner would then keep what it finds.</p>
 *
 * @param <T> configuration model type
 */
public abstract class ConfigExtensionProvider<T> extends ConfigProvider<T> {
	/**
	 * Registers a reloadable view of a document owned by another provider.
	 *
	 * @param basePath directory containing the document
	 * @param fileName document name, optionally including its format extension
	 * @param type configuration model the view reads the document as
	 * @param reloadables registry used to refresh this provider
	 */
	protected ConfigExtensionProvider(
			@NotNull Path basePath,
			@NotNull String fileName,
			@NotNull Class<? extends T> type,
			@NotNull Registry<Reloadable> reloadables
	) {
		super(basePath, fileName, type, reloadables);
	}
}
