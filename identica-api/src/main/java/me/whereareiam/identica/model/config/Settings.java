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
		 * Default policy for concurrent sessions.
		 */
		private @NotNull SessionConcurrencyPolicy concurrencyPolicy;
		/**
		 * Time-to-live used to keep live-session cache entries available while the player is online.
		 */
		private @NotNull Duration activeTtl;
		/**
		 * Returns active session TTL in milliseconds with validation.
		 *
		 * @return active session TTL in milliseconds
		 */
		public long activeTtlMillis() {
			if (activeTtl.isZero() || activeTtl.isNegative()) {
				throw new IllegalStateException("settings.sessions.activeTtl must be positive");
			}

			return activeTtl.toMillis();
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
