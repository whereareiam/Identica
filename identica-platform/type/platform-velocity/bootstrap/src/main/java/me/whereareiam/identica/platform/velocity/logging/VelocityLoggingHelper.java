package me.whereareiam.identica.platform.velocity.logging;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.Setter;
import me.whereareiam.identica.logging.LoggingHelper;
import me.whereareiam.identica.model.config.Settings;
import org.slf4j.Logger;

public final class VelocityLoggingHelper implements LoggingHelper {
	@Setter
	private static Logger logger;
	private final Provider<Settings> settings;

	@Inject
	public VelocityLoggingHelper(Provider<Settings> settings) {
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
			logger.warn(format(message, objects));
	}

	@Override
	public void severe(String message, Object... objects) {
		if (logger != null && settings.get().getLevel() >= 0)
			logger.error(format(message, objects));
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
