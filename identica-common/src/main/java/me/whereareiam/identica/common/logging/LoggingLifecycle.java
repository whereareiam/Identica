package me.whereareiam.identica.common.logging;

import com.google.inject.Inject;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.logging.LoggingHelper;
import org.jetbrains.annotations.NotNull;

/**
 * Connects Identica's {@link Logger} to the platform's {@link LoggingHelper}, which writes to the proxy console at
 * the level the settings name. It starts once the configuration is loaded, so the level can be read, and before
 * anything that opens a store, so what the stores report on startup reaches the console.
 */
public final class LoggingLifecycle implements RuntimeLifecycle {
	private final LoggingHelper loggingHelper;

	@Inject
	public LoggingLifecycle(@NotNull LoggingHelper loggingHelper) {
		this.loggingHelper = loggingHelper;
	}

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.LOGGING;
	}

	@Override
	public void initialize() {
		Logger.init(loggingHelper);
	}

	/**
	 * Keeps the log connected: what Identica reports while it finishes shutting down still reaches the console.
	 */
	@Override
	public void shutdown() {
	}
}
