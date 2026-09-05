package me.whereareiam.identica.trait.authoritative.username.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.configura.ConfigDocument;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Messages owned by the username identity subsystem and stored in identity/username/messages.
 */
@Getter
@Setter
@ToString
public class AuthoritativeUsernameMessages extends ConfigDocument {
	/**
	 * Messages for each username pipeline stage.
	 *
	 * @return configured message sections
	 */
	private @NotNull Pipeline pipeline;

	/**
	 * Messages used while preparing, synchronizing, and persisting usernames.
	 */
	@Getter
	@Setter
	@ToString
	public static class Pipeline {
		/**
		 * Username preparation failures.
		 *
		 * @return configured message sections
		 */
		private @NotNull Prepare prepare;
		/**
		 * Username synchronization failures shared by all scenarios.
		 *
		 * @return configured message sections
		 */
		private @NotNull Identity identity;
		/**
		 * Username persistence and conflict handling messages.
		 *
		 * @return configured message sections
		 */
		private @NotNull Policy policy;

		/**
		 * Failures encountered while preparing an account username.
		 */
		@Getter
		@Setter
		@ToString
		public static class Prepare {
			/**
			 * Message lines shown when username preparation lacks required account data.
			 *
			 * @return configured message lines
			 */
			private @NotNull List<String> failed;
		}

		/**
		 * Shared username synchronization failures for authentication, registration, and migration.
		 */
		@Getter
		@Setter
		@ToString
		public static class Identity {
			/**
			 * Message lines shown when a scenario cannot synchronize its username.
			 *
			 * @return configured message lines
			 */
			private @NotNull List<String> synchronizationFailed;
		}

		/**
		 * Username persistence, conflict-denial, and entrypoint-selection messages.
		 */
		@Getter
		@Setter
		@ToString
		public static class Policy {
			/**
			 * Message lines shown when username persistence fails.
			 *
			 * @return configured message lines
			 */
			private @NotNull List<String> persistenceFailed;
			/**
			 * Fallback message lines when username conflict resolution denies the incoming player.
			 *
			 * @return configured message lines
			 */
			private @NotNull List<String> conflictDenied;
			/**
			 * Message lines requesting an entrypoint, supporting incomingProvider, existingProvider, incomingHost, and existingHost placeholders.
			 *
			 * @return configured message lines
			 */
			private @NotNull List<String> entrypointRequired;
		}
	}
}
