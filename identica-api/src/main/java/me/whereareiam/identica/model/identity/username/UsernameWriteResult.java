package me.whereareiam.identica.model.identity.username;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Whether a username was written for an account, or which account holds it already. A write that the storage
 * failed for throws instead.
 */
@Getter
@ToString
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class UsernameWriteResult {
	private static final UsernameWriteResult WRITTEN = new UsernameWriteResult(true, null);

	private final boolean written;

	/**
	 * The account that holds the username, or {@code null} when it was written.
	 */
	private final @Nullable UUID holderUniqueId;

	/**
	 * Returns the result of a written username.
	 *
	 * @return result
	 */
	public static @NotNull UsernameWriteResult written() {
		return WRITTEN;
	}

	/**
	 * Returns the result of a username another account holds.
	 *
	 * @param holderUniqueId the account that holds it
	 * @return result
	 */
	public static @NotNull UsernameWriteResult taken(@NotNull UUID holderUniqueId) {
		return new UsernameWriteResult(false, holderUniqueId);
	}
}
