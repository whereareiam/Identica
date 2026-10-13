package me.whereareiam.identica.common.identity.username;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.common.config.provider.UsernameConflictsProvider;
import me.whereareiam.identica.common.config.provider.UsernameMessagesProvider;
import me.whereareiam.identica.common.identity.username.outcome.RefuseOutcome;
import me.whereareiam.identica.common.identity.username.outcome.RenameArrivingOutcome;
import me.whereareiam.identica.common.identity.username.outcome.RenameHolderOutcome;
import me.whereareiam.identica.conflict.ConflictOutcome;
import me.whereareiam.identica.conflict.ConflictService;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class UsernameLifecycle implements RuntimeLifecycle {
	private final Registry<Reloadable> reloadables;
	private final UsernameConflictsProvider conflictsProvider;
	private final UsernameMessagesProvider messagesProvider;
	private final ConflictService conflicts;
	private final UsernameDuplicateWarning duplicateWarning;
	private final Deque<Runnable> cleanup = new ArrayDeque<>();

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.RUNTIME;
	}

	@Override
	public void initialize() {
		cleanup.addFirst(() -> reloadables.unregister(conflictsProvider));
		cleanup.addFirst(() -> reloadables.unregister(messagesProvider));
		conflictsProvider.get();
		register(new RefuseOutcome());
		register(new RenameArrivingOutcome());
		register(new RenameHolderOutcome());

		duplicateWarning.warn();
	}

	@Override
	public void shutdown() {
		RuntimeException failure = null;
		while (!cleanup.isEmpty()) {
			try {
				cleanup.removeFirst().run();
			} catch (RuntimeException exception) {
				if (failure == null) failure = exception;
				else failure.addSuppressed(exception);
			}
		}
		if (failure != null) throw failure;
	}

	private void register(@NotNull ConflictOutcome<?, ?, ?> outcome) {
		cleanup.addFirst(() -> conflicts.unregister(outcome));
		conflicts.register(outcome);
	}
}
