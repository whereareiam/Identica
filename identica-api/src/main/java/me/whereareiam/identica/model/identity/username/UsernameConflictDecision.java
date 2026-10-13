package me.whereareiam.identica.model.identity.username;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.type.identity.username.UsernameConflictAction;
import me.whereareiam.identica.type.identity.username.UsernameHolderOnline;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What an outcome of a username conflict decides: refuse the arriving player, or rename one side the given way.
 */
@Getter
@ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public final class UsernameConflictDecision {
	private final @NotNull UsernameConflictAction action;

	/**
	 * How the renamed side is renamed, or {@code null} for a refusal.
	 */
	private final @Nullable UsernameRename rename;

	/**
	 * What happens to a holder who is online, or {@code null} unless the holder is renamed.
	 */
	private final @Nullable UsernameHolderOnline holderOnline;

	/**
	 * Refuses the arriving player.
	 *
	 * @return decision
	 */
	public static @NotNull UsernameConflictDecision refuse() {
		return new UsernameConflictDecision(UsernameConflictAction.REFUSE, null, null);
	}

	/**
	 * Renames the arriving account.
	 *
	 * @param rename how to rename it
	 * @return decision
	 */
	public static @NotNull UsernameConflictDecision renameArriving(@NotNull UsernameRename rename) {
		return new UsernameConflictDecision(UsernameConflictAction.RENAME_ARRIVING, rename, null);
	}

	/**
	 * Renames the holder; the arriving player's session name while the holder keeps playing is made the same way.
	 *
	 * @param rename how to rename the holder, with what happens to a holder who is online
	 * @return decision
	 */
	public static @NotNull UsernameConflictDecision renameHolder(@NotNull UsernameHolderRename rename) {
		return new UsernameConflictDecision(UsernameConflictAction.RENAME_HOLDER, rename, rename.getHolderOnline());
	}
}
