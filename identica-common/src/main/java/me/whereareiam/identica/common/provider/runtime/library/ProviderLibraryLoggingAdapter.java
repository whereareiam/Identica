package me.whereareiam.identica.common.provider.runtime.library;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.attache.LoggingHelper;
import me.whereareiam.attache.type.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ProviderLibraryLoggingAdapter implements LoggingHelper {
	private final me.whereareiam.identica.logging.LoggingHelper loggingHelper;

	@Override
	public void log(@NotNull Level level, @NotNull String message) {
		log(level, message, null);
	}

	@Override
	public void log(Level level, @NotNull String message, @Nullable Throwable throwable) {
		switch (level) {
			case WARN -> loggingHelper.warn(message);
			case ERROR -> loggingHelper.severe(message);
			case DEBUG -> loggingHelper.debug(message);
			default -> loggingHelper.info(message);
		}

		if (throwable != null) {
			loggingHelper.debug("Dependency load error: %s", throwable.getMessage());
		}
	}
}
