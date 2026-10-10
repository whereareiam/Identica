package me.whereareiam.identica.platform.bungeecord.logging;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.Setter;
import me.whereareiam.identica.logging.LoggingHelper;
import me.whereareiam.identica.model.config.Settings;

import java.util.logging.Logger;

public final class BungeeCordLoggingHelper implements LoggingHelper {
	@Setter
	private static Logger logger;
	private final Provider<Settings> settings;

	@Inject
	public BungeeCordLoggingHelper(Provider<Settings> settings) {
		this.settings = settings;
	}

	@Override
	public void info(String message, Object... objects) {
		if (logger != null && settings.get().getLevel() >= 2)
			logger.info(format(message, objects));
	}

	@Override
	public void warn(String message, Object... objects) {
		if (logger != null && settings.get().getLevel() >= 1)
			logger.warning(format(message, objects));
	}

	@Override
	public void severe(String message, Object... objects) {
		if (logger != null && settings.get().getLevel() >= 0)
			logger.severe(format(message, objects));
	}

	@Override
	public void debug(String message, Object... objects) {
		if (logger != null && settings.get().getLevel() >= 3)
			logger.info(format(message, objects));
	}

	@Override
	public void trace(String message, Object... objects) {
		if (logger != null && settings.get().getLevel() >= 4)
			logger.info(format(message, objects));
	}

	private String format(String message, Object... objects) {
		if (message == null) return "null";
		return objects == null || objects.length == 0 ? message : String.format(message, objects);
	}
}
