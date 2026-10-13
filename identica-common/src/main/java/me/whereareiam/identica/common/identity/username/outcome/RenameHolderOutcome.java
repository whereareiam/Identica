package me.whereareiam.identica.common.identity.username.outcome;

import me.whereareiam.identica.conflict.ConflictOutcome;
import me.whereareiam.identica.model.conflict.ConflictKind;
import me.whereareiam.identica.model.identity.username.UsernameConflict;
import me.whereareiam.identica.model.identity.username.UsernameConflictDecision;
import me.whereareiam.identica.model.identity.username.UsernameHolderRename;
import me.whereareiam.identica.type.conflict.ConflictSide;
import org.jetbrains.annotations.NotNull;

/**
 * The built-in outcome {@code rename-holder}: the arriving account gets the username and the holder gets a renamed
 * one. Its settings also make the arriving player's session name while the holder keeps playing, and the temporary
 * name of a holder whose name is stale.
 */
public final class RenameHolderOutcome implements ConflictOutcome<UsernameConflict, UsernameConflictDecision, UsernameHolderRename> {
	/** Id rules name the outcome by. */
	public static final String ID = "rename-holder";

	@Override
	public @NotNull ConflictKind<UsernameConflict, UsernameConflictDecision> kind() {
		return UsernameConflict.KIND;
	}

	@Override
	public @NotNull String id() {
		return ID;
	}

	@Override
	public @NotNull Class<UsernameHolderRename> settingsType() {
		return UsernameHolderRename.class;
	}

	@Override
	public @NotNull UsernameHolderRename defaults() {
		return new UsernameHolderRename();
	}

	@Override
	public @NotNull ConflictSide actsOn() {
		return ConflictSide.HOLDER;
	}

	@Override
	public @NotNull UsernameConflictDecision decide(@NotNull UsernameConflict conflict, @NotNull UsernameHolderRename settings) {
		return UsernameConflictDecision.renameHolder(settings);
	}
}
