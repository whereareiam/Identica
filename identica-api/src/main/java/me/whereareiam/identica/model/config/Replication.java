package me.whereareiam.identica.model.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Replication configuration used for cross-instance replication.
 */
@Getter
@Setter
@ToString
public class Replication {
	/**
	 * Shortest accepted heartbeat timeout: below it a renewal every third of the timeout would be more frequent than
	 * once a second.
	 */
	public static final Duration MINIMUM_HEARTBEAT_TIMEOUT = Duration.ofSeconds(3);

	private boolean enabled;
	private @NotNull String serverId;
	/**
	 * How long a record this proxy owns, such as a session or the hold of a username being logged in, stays stored
	 * after the proxy last renewed it. The proxy renews each record it holds every third of this time, so the
	 * records of a proxy that stopped or crashed without releasing them disappear this long after it stopped.
	 */
	private @NotNull Duration heartbeatTimeout;
	private @NotNull Redis redis;
	private @NotNull Cache cache;

	/**
	 * Returns the heartbeat timeout in milliseconds with validation.
	 *
	 * @return heartbeat timeout in milliseconds
	 * @throws IllegalStateException when the timeout is shorter than {@link #MINIMUM_HEARTBEAT_TIMEOUT}
	 */
	public long heartbeatTimeoutMillis() {
		if (heartbeatTimeout.compareTo(MINIMUM_HEARTBEAT_TIMEOUT) < 0)
			throw new IllegalStateException("replication.heartbeatTimeout must be at least " + MINIMUM_HEARTBEAT_TIMEOUT.toSeconds() + "s");

		return heartbeatTimeout.toMillis();
	}

	/**
	 * Redis settings used by the replication adapter.
	 */
	@Getter
	@Setter
	@ToString
	public static class Redis {
		private @NotNull String host;
		private int port;

		private @NotNull String password;

		private boolean ssl;
		private int timeout;

		private @NotNull Channels channels;
	}

	/**
	 * Cache namespaces used by synchronized caches.
	 */
	@Getter
	@Setter
	@ToString
	public static class Cache {
		private @NotNull String reservations;
		private @NotNull String instructions;
		private @NotNull Delivery delivery;
		private @NotNull Sessions sessions;
		/**
		 * Cache namespace for pipeline state.
		 */
		private @NotNull String pipelineState;
		/**
		 * Cache namespace for provider attempts.
		 */
		private @NotNull String attempts;
	}

	@Getter
	@Setter
	@ToString
	public static class Delivery {
		private @NotNull String requests;
		private @NotNull String connectionIndex;
		private @NotNull String accountIndex;
	}

	/**
	 * Session cache namespaces.
	 */
	@Getter
	@Setter
	@ToString
	public static class Sessions {
		private @NotNull String records;
		private @NotNull String accounts;
		private @NotNull String subjects;
	}

	/**
	 * Channel names used for replication events.
	 */
	@Getter
	@Setter
	@ToString
	public static class Channels {
		private @NotNull String accountUpdates;
		private @NotNull String sessions;
		private @NotNull String conflicts;
		/**
		 * Channel used for generic replicated events.
		 */
		private @NotNull String events;
	}
}
