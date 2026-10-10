package me.whereareiam.identica.model.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.configura.annotation.merge.Merge;
import me.whereareiam.configura.type.merge.UnknownEntries;
import me.whereareiam.identica.model.Event;
import me.whereareiam.identica.type.identity.UniqueIdMode;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;

/**
 * Root settings configuration model.
 */
@Getter
@Setter
@ToString
public class Settings {
	/**
	 * Verbosity level for logging.
	 */
	private int level;
	private @NotNull Identity identity = new Identity();
	private @NotNull Sessions sessions = new Sessions();
	private @NotNull Listeners listeners = new Listeners();

	/**
	 * Identity configuration settings.
	 */
	@Getter
	@Setter
	@ToString
	public static class Identity {
		/**
		 * Strategy used to assign UUIDs to newly discovered accounts.
		 */
		private @NotNull UniqueIdMode uniqueIdMode;
		/**
		 * Time-to-live for reserved account identities.
		 */
		private @NotNull Duration reservationTtl;

		/**
		 * Returns reservation TTL in milliseconds with validation.
		 *
		 * @return reservation TTL in milliseconds
		 */
		public long reservationTtlMillis() {
			if (reservationTtl.isZero() || reservationTtl.isNegative()) {
				throw new IllegalStateException("settings.identity.reservationTtl must be positive");
			}

			return reservationTtl.toMillis();
		}
	}

	/**
	 * Session behavior settings.
	 */
	@Getter
	@Setter
	@ToString
	public static class Sessions {
		/**
		 * Shortest accepted heartbeat timeout: below it a refresh every third of the timeout would be
		 * more frequent than once a second.
		 */
		public static final Duration MINIMUM_HEARTBEAT_TIMEOUT = Duration.ofSeconds(3);

		/**
		 * Default policy for concurrent sessions.
		 */
		private @NotNull SessionConcurrencyPolicy concurrencyPolicy;
		/**
		 * How long a session stays stored after the proxy holding its connection last refreshed it. The
		 * proxy refreshes each session it holds every third of this time, so the sessions of a proxy
		 * that stopped without closing them disappear this long after it stopped.
		 */
		private @NotNull Duration heartbeatTimeout;

		/**
		 * Returns the heartbeat timeout in milliseconds with validation.
		 *
		 * @return heartbeat timeout in milliseconds
		 * @throws IllegalStateException when the timeout is shorter than {@link #MINIMUM_HEARTBEAT_TIMEOUT}
		 */
		public long heartbeatTimeoutMillis() {
			if (heartbeatTimeout.compareTo(MINIMUM_HEARTBEAT_TIMEOUT) < 0) {
				throw new IllegalStateException("settings.sessions.heartbeatTimeout must be at least "
						+ MINIMUM_HEARTBEAT_TIMEOUT.toSeconds() + "s");
			}

			return heartbeatTimeout.toMillis();
		}
	}

	/**
	 * Listener registration settings.
	 */
	@Getter
	@Setter
	@ToString
	public static class Listeners {
		@Merge(unknownEntries = UnknownEntries.REJECT)
		private @NotNull Map<String, Event> events;
	}
}
