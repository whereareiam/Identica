package me.whereareiam.identica.common.identity.username;

import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.common.config.provider.UsernameConflictsProvider;
import me.whereareiam.identica.common.config.provider.UsernameMessagesProvider;
import me.whereareiam.identica.conflict.ConflictService;
import me.whereareiam.identica.conflict.ConflictOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Username Lifecycle")
class UsernameLifecycleTest {
	@SuppressWarnings("unchecked")
	private final Registry<Reloadable> reloadables = mock(Registry.class);
	private final UsernameConflictsProvider conflictsProvider = mock(UsernameConflictsProvider.class);
	private final UsernameMessagesProvider messagesProvider = mock(UsernameMessagesProvider.class);
	private final ConflictService conflicts = mock(ConflictService.class);
	private final UsernameDuplicateWarning duplicateWarning = mock(UsernameDuplicateWarning.class);
	private final UsernameLifecycle lifecycle =
			new UsernameLifecycle(reloadables, conflictsProvider, messagesProvider, conflicts, duplicateWarning);

	@Test
	@DisplayName("Loads the conflict rules and registers the built-in outcomes")
	void initializeLoadsRulesAndRegistersOutcomes() {
		lifecycle.initialize();

		InOrder order = inOrder(conflictsProvider, conflicts);
		order.verify(conflictsProvider).get();
		order.verify(conflicts, times(3)).register(any(ConflictOutcome.class));
		verifyNoInteractions(reloadables);
	}

	@Test
	@DisplayName("Warns about usernames that several accounts share")
	void initializeWarnsAboutDuplicateUsernames() {
		lifecycle.initialize();

		verify(duplicateWarning).warn();
	}

	@Test
	@DisplayName("Shutdown undoes every registration in reverse order")
	void shutdownUndoesRegistrations() {
		lifecycle.initialize();
		lifecycle.shutdown();

		InOrder order = inOrder(conflicts, reloadables);
		order.verify(conflicts, times(3)).unregister(any(ConflictOutcome.class));
		order.verify(reloadables).unregister(messagesProvider);
		order.verify(reloadables).unregister(conflictsProvider);
	}
}
