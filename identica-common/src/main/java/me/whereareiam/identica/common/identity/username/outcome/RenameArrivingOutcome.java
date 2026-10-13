package me.whereareiam.identica.common.identity.username.outcome;

import me.whereareiam.identica.conflict.ConflictOutcome;
import me.whereareiam.identica.model.conflict.ConflictKind;
import me.whereareiam.identica.model.identity.username.UsernameConflict;
import me.whereareiam.identica.model.identity.username.UsernameConflictDecision;
import me.whereareiam.identica.model.identity.username.UsernameRename;
import org.jetbrains.annotations.NotNull;

/**
 * The built-in outcome {@code rename-arriving}: the holder keeps the username and the arriving account gets a
 * renamed one.
 */
public final class RenameArrivingOutcome implements ConflictOutcome<UsernameConflict, UsernameConflictDecision, UsernameRename> {
	/** Id rules name the outcome by. */
	public static final String ID = "rename-arriving";

	@Override
	public @NotNull ConflictKind<UsernameConflict, UsernameConflictDecision> kind() {
		return UsernameConflict.KIND;
	}

	@Override
	public @NotNull String id() {
		return ID;
	}

	@Override
	public @NotNull Class<UsernameRename> settingsType() {
		return UsernameRename.class;
	}

	@Override
	public @NotNull UsernameRename defaults() {
		return new UsernameRename();
	}

	@Override
	public @NotNull UsernameConflictDecision decide(@NotNull UsernameConflict conflict, @NotNull UsernameRename settings) {
		return UsernameConflictDecision.renameArriving(settings);
	}
}
