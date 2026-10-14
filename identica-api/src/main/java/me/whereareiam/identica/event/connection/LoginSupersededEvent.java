package me.whereareiam.identica.event.connection;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.event.base.ReplicatedEvent;
import me.whereareiam.identica.event.base.SynchronousEvent;
import me.whereareiam.identica.model.SessionConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Event fired when a connection won the login of an account: it finished logging in, or a provider proved that it
 * belongs to the account. Every proxy disconnects its other connections that are still logging in to the account or
 * under its username, since an account has one connection that logs in. Connections that already logged in stay
 * governed by the session concurrency policy.
 *
 * <p>The event is replicated, so it reaches the connections of every proxy of a network.</p>
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class LoginSupersededEvent implements ReplicatedEvent, SynchronousEvent {
	/**
	 * Account the winner logs in to.
	 */
	private @NotNull UUID accountUniqueId;
	/**
	 * Username the winner logs in under.
	 */
	private @NotNull String username;
	/**
	 * Connection that won, named with the proxy that holds it.
	 */
	private @NotNull SessionConnection winner;
	private @Nullable UUID replicationEventId;
	private @Nullable String replicationOriginServerId;

	/**
	 * Creates the event for a winner of this proxy.
	 *
	 * @param accountUniqueId account the winner logs in to
	 * @param username username the winner logs in under
	 * @param winner connection that won, named with the proxy that holds it
	 */
	public LoginSupersededEvent(@NotNull UUID accountUniqueId, @NotNull String username, @NotNull SessionConnection winner) {
		this(accountUniqueId, username, winner, null, null);
	}
}
