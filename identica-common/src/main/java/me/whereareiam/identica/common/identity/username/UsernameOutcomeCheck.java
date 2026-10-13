package me.whereareiam.identica.common.identity.username;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.common.config.provider.UsernameConflictsProvider;
import me.whereareiam.identica.lifecycle.RuntimeLifecycle;
import me.whereareiam.identica.lifecycle.StartupPhase;
import org.jetbrains.annotations.NotNull;

/**
 * Reports username conflict rules that name an outcome nobody registered, once the providers have registered theirs,
 * and from then on whenever the rules are reloaded.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public final class UsernameOutcomeCheck implements RuntimeLifecycle {
	private final UsernameConflictsProvider conflictsProvider;

	@Override
	public @NotNull StartupPhase phase() {
		return StartupPhase.CONNECTIONS;
	}

	@Override
	public void initialize() {
		conflictsProvider.checkOutcomes();
	}

	@Override
	public void shutdown() {
		// The check holds nothing to release.
	}
}
