package me.whereareiam.identica.feature.verification.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Verification feature message configuration document.
 */
@Getter
@Setter
@ToString
public class VerificationMessages {
	private @NotNull Methods methods;
	private @NotNull Commands commands;

	@Getter
	@Setter
	@ToString
	public static class Commands {
		private @NotNull String playerOnly;
		private @NotNull String notAllowed;
		private @NotNull Status status;
		private @NotNull Enroll enroll;
		private @NotNull Confirm confirm;
		private @NotNull Use use;
		private @NotNull Disable disable;
		private @NotNull Cancel cancel;
		private @NotNull Admin admin;

		@Getter
		@Setter
		@ToString
		public static class Status {
			private @NotNull List<String> body;
			private @NotNull EntryFormat enrollmentEntry;
			private @NotNull EntryFormat selectionEntry;
			private @NotNull String emptyEnrollments;
			private @NotNull String emptySelections;
		}

		@Getter
		@Setter
		@ToString
		public static class Enroll {
			private @NotNull String unknownMethod;
			private @NotNull String alreadyEnrolled;
		}

		@Getter
		@Setter
		@ToString
		public static class Confirm {
			private @NotNull String noPending;
			private @NotNull String invalidCode;
			private @NotNull String protectedActionSelectionRequired;
			private @NotNull String protectedActionSessionRequired;
			private @NotNull String methodUnavailable;
			private @NotNull String enabled;
			private @NotNull String autoSelected;
			private @NotNull RecoveryCodes recoveryCodes;

			@Getter
			@Setter
			@ToString
			public static class RecoveryCodes {
				private @NotNull Layout layout = Layout.TWO_COLUMN;
				private @NotNull List<String> body;
				private @NotNull EntryFormat singleColumnEntry;
				private @NotNull EntryFormat twoColumnEntry;
				private @NotNull String empty;

				public enum Layout {
					SINGLE_COLUMN,
					TWO_COLUMN
				}
			}
		}

		@Getter
		@Setter
		@ToString
		public static class Use {
			private @NotNull String providerNotFound;
			private @NotNull String providerUnsupported;
			private @NotNull String providerVerificationDisabled;
			private @NotNull String methodNotEnrolled;
			private @NotNull String methodDisabledForProvider;
			private @NotNull String alreadySelected;
			private @NotNull String updated;
		}

		@Getter
		@Setter
		@ToString
		public static class Disable {
			private @NotNull String methodNotEnrolled;
			private @NotNull String protectedPrompt;
			private @NotNull String disabled;
		}

		@Getter
		@Setter
		@ToString
		public static class Cancel {
			private @NotNull String noPending;
			private @NotNull String cancelled;
			private @NotNull String cancelledProtectedAction;
		}

		@Getter
		@Setter
		@ToString
		public static class Admin {
			private @NotNull Reset reset;

			@Getter
			@Setter
			@ToString
			public static class Reset {
				private @NotNull String targetNotFound;
				private @NotNull String completed;
			}
		}

		@Getter
		@Setter
		@ToString
		public static class EntryFormat {
			private @NotNull String format;
			private @NotNull String emptyFormat;
		}
	}

	@Getter
	@Setter
	@ToString
	public static class Methods {
		private @NotNull Totp totp;

		@Getter
		@Setter
		@ToString
		public static class Totp {
			private @NotNull List<String> pending;
		}
	}
}
