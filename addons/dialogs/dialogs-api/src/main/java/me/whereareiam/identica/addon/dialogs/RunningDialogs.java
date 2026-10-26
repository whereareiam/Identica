package me.whereareiam.identica.addon.dialogs;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Holds the running addon for {@link Dialogs}'s static accessors and the actions waiting for it.
 */
final class RunningDialogs {
	private static final List<Consumer<Dialogs>> WAITING = new ArrayList<>();
	private static @Nullable Dialogs running;

	private RunningDialogs() {
		throw new UnsupportedOperationException("This class cannot be instantiated");
	}

	static synchronized @NotNull Dialogs get() {
		if (running == null) throw new IllegalStateException("The dialogs addon is not running");

		return running;
	}

	static synchronized @NotNull Optional<Dialogs> find() {
		return Optional.ofNullable(running);
	}

	static void whenReady(@NotNull Consumer<Dialogs> action) {
		Dialogs ready;
		synchronized (RunningDialogs.class) {
			ready = running;
			if (ready == null) {
				WAITING.add(action);
				return;
			}
		}
		action.accept(ready);
	}

	static void register(@NotNull Dialogs dialogs) {
		List<Consumer<Dialogs>> actions;
		synchronized (RunningDialogs.class) {
			if (running != null) throw new IllegalStateException("The dialogs addon is already running");

			running = dialogs;
			actions = List.copyOf(WAITING);
			WAITING.clear();
		}
		actions.forEach(action -> action.accept(dialogs));
	}

	static synchronized void unregister() {
		running = null;
	}
}
