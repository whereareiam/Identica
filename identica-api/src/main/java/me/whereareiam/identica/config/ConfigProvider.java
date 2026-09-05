package me.whereareiam.identica.config;

import com.google.inject.Provider;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

/**
 * Loads and caches a configuration document, refreshing it when reloaded.
 * Installation migrations are separate from document loading.
 *
 * @param <T> configuration model type
 */
public abstract class ConfigProvider<T> implements Provider<T>, Reloadable {
	private final @NotNull Path path;
	private final @NotNull Class<? extends T> type;
	private @Nullable T value;

	/**
	 * Registers a reloadable document provider.
	 *
	 * @param basePath directory containing the document
	 * @param fileName document name, optionally including its format extension
	 * @param type default configuration model
	 * @param reloadables registry used to refresh this provider
	 */
	protected ConfigProvider(
			@NotNull Path basePath,
			@NotNull String fileName,
			@NotNull Class<? extends T> type,
			@NotNull Registry<Reloadable> reloadables
	) {
		this.path = basePath.resolve(fileName);
		this.type = type;
		reloadables.register(this);
	}

	/**
	 * Returns the cached configuration, loading it on first access.
	 *
	 * @return current configuration
	 */
	@Override
	public @NotNull T get() {
		if (value != null) return value;

		value = load();
		return value;
	}

	/**
	 * Reloads the document and replaces the cached model after a successful load.
	 */
	@Override
	public void reload() {
		value = load();
	}

	/**
	 * Returns the configured document path before Configura resolves its extension.
	 *
	 * @return document path
	 */
	protected final @NotNull Path getPath() {
		return path;
	}

	/**
	 * Loads the document, applies configured defaults, and persists the validated result.
	 *
	 * @return loaded configuration
	 */
	protected @NotNull T load() {
		return configura().update(path, resolveType(path));
	}

	/**
	 * Selects the configuration model for the document.
	 *
	 * @param path document path
	 * @return model type used for loading
	 */
	protected @NotNull Class<? extends T> resolveType(@NotNull Path path) {
		return type;
	}

	/**
	 * Reads a document without rewriting it.
	 *
	 * @param path source document
	 * @param type model type
	 * @param <R> result type
	 * @return deserialized model
	 */
	protected final <R> @NotNull R read(@NotNull Path path, @NotNull Class<R> type) {
		return configura().read(path, type);
	}

	/**
	 * Returns the Configura instance used by this provider.
	 *
	 * @return effective Configura instance
	 */
	protected @NotNull Configura configura() {
		return Config.configured();
	}
}
