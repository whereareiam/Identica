package me.whereareiam.identica.feature.restriction.join.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.configura.ConfigDocument;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Message configuration for the typed join restriction module.
 */
@Getter
@Setter
@ToString
public class JoinRestrictionMessages extends ConfigDocument {
	private @NotNull List<String> denied;
	private @NotNull Commands commands;

	@Getter
	@Setter
	@ToString
	public static class Commands {
		private @NotNull String enabled;
		private @NotNull String disabled;
		private @NotNull String enableFailed;
		private @NotNull String providerNotFound;
		private @NotNull Status status;

		@Getter
		@Setter
		@ToString
		public static class Status {
			private @NotNull String notFound;
			private @NotNull List<String> body;
			private @NotNull Labels labels;
			private @NotNull Listing list;

			@Getter
			@Setter
			@ToString
			public static class Labels {
				private @NotNull String enabled;
				private @NotNull String disabled;
			}

			@Getter
			@Setter
			@ToString
			public static class Listing {
				private @NotNull List<String> body;
				private @NotNull String empty;
				private @NotNull Entries entries;

				@Getter
				@Setter
				@ToString
				public static class Entries {
					private @NotNull String populated;
					private @NotNull String empty;
				}
			}
		}
	}
}
