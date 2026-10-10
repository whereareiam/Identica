package me.whereareiam.identica.model.identity;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Request for an account lifecycle operation.
 */
@Getter
@ToString
@Builder(toBuilder = true)
public class AccountOperationRequest {
	/**
	 * Account targeted by the operation.
	 */
	private final @NotNull Account account;
	/**
	 * Whether a player connected with the account is disconnected when its session is closed.
	 * The decision does not depend on {@link #getDisconnectMessage()} having text.
	 */
	private final boolean disconnect;
	/**
	 * Reason shown to a player who is disconnected; empty for a disconnect without one.
	 */
	@Builder.Default
	private final @NotNull String disconnectMessage = "";
	/**
	 * Optional reason for audit or downstream listeners.
	 */
	private final @Nullable String reason;
}
