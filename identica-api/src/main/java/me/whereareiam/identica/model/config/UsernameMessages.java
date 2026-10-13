package me.whereareiam.identica.model.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Messages owned by the username identity subsystem and stored in identity/username/messages.
 */
@Getter
@Setter
@ToString
public class UsernameMessages implements MessageDocument {
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
		 * Username persistence and conflict messages.
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
			 * Message lines shown when a player is refused because another account holds their username, or because
			 * no free renamed username could be made, with the {@code username} placeholder.
			 *
			 * @return configured message lines
			 */
			private @NotNull List<String> taken;
			/**
			 * Disconnect message lines for a player whose username was given to the player who owns it, with the
			 * {@code username} placeholder for the given username and {@code renamed} for the player's new one.
			 *
			 * @return configured message lines
			 */
			private @NotNull List<String> holderReplaced;
		}
	}
}
