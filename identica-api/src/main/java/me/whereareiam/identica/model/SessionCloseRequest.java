package me.whereareiam.identica.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Request to close a session across the local instance and replicated peers.
 *
 * <p>Whether the player is disconnected is the caller's decision and is carried by
 * {@link #isDisconnect()}, not by the message: a request that disconnects does so with or without
 * text. The disconnect message is resolved by the caller before replication so every instance
 * uses the same message even when local configuration differs.</p>
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class SessionCloseRequest {
	/**
	 * Unique id for this close request, used for deduplication.
	 */
	private @Nullable UUID requestId;
	/**
	 * Server id that originated the request.
	 */
	private @Nullable String originServerId;
	/**
	 * Identity id whose session should be closed.
	 */
	private @NotNull UUID uniqueId;
	/**
	 * Whether the player whose session is closed is also disconnected, on whichever instance holds
	 * the connection. Left false for a close that follows the player leaving.
	 */
	private boolean disconnect;
	/**
	 * Optional already-resolved reason shown when {@link #isDisconnect()} disconnects the player.
	 * Without it the player is disconnected with an empty reason.
	 */
	private @Nullable String disconnectMessage;
}
