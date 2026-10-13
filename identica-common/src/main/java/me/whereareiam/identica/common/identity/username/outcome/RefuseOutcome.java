package me.whereareiam.identica.common.identity.username.outcome;

import me.whereareiam.identica.conflict.ConflictOutcome;
import me.whereareiam.identica.model.conflict.ConflictKind;
import me.whereareiam.identica.model.conflict.NoSettings;
import me.whereareiam.identica.model.identity.username.UsernameConflict;
import me.whereareiam.identica.model.identity.username.UsernameConflictDecision;
import org.jetbrains.annotations.NotNull;

/**
 * The built-in outcome {@code refuse}: the arriving player is refused and the holder keeps the username.
 */
public final class RefuseOutcome implements ConflictOutcome<UsernameConflict, UsernameConflictDecision, NoSettings> {
	/** Id rules name the outcome by. */
	public static final String ID = "refuse";

	@Override
	public @NotNull ConflictKind<UsernameConflict, UsernameConflictDecision> kind() {
		return UsernameConflict.KIND;
	}

	@Override
	public @NotNull String id() {
		return ID;
	}

	@Override
	public @NotNull Class<NoSettings> settingsType() {
		return NoSettings.class;
	}

	@Override
	public @NotNull NoSettings defaults() {
		return new NoSettings();
	}

	@Override
	public @NotNull UsernameConflictDecision decide(@NotNull UsernameConflict conflict, @NotNull NoSettings settings) {
		return UsernameConflictDecision.refuse();
	}
}
