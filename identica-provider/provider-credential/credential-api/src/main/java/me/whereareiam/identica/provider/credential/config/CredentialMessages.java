package me.whereareiam.identica.provider.credential.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import me.whereareiam.identica.model.config.MessageDocument;

/**
 * Message configuration for the credential provider.
 */
@Getter
@Setter
@ToString
public class CredentialMessages implements MessageDocument {
	private Scenario scenario;
	private Completion completion;
	private Password password;
	private ChangePassword changePassword;
	private Commands commands;

	@Getter
	@Setter
	@ToString
	public static class Completion {
		private Pipeline recognition;
		private Pipeline authentication;
		private Pipeline registration;
		private Pipeline migration;

		@Getter
		@Setter
		@ToString
		public static class Pipeline {
			private Title title;
			private List<String> body;

			@Getter
			@Setter
			@ToString
			public static class Title {
				private String title;
				private String subtitle;
			}
		}
	}

	@Getter
	@Setter
	@ToString
	public static class Scenario {
		private Authentication authentication;
		private Registration registration;
		private Migration migration;

		@Getter
		@Setter
		@ToString
		public static class Migration {
			private Verification verification;
			private Setup setup;

			@Getter
			@Setter
			@ToString
			public static class Verification {
				private List<String> prompt;
				private Status status;

				@Getter
				@Setter
				@ToString
				public static class Status {
					private String invalid;
				}
			}

			@Getter
			@Setter
			@ToString
			public static class Setup {
				private List<String> prompt;
				private List<String> confirmPrompt;
				private Status status;

				@Getter
				@Setter
				@ToString
				public static class Status {
					private String disabled;
					private String mismatch;
					private String noPending;
				}
			}
		}

		@Getter
		@Setter
		@ToString
		public static class Registration {
			private List<String> prompt;
			private List<String> confirmPrompt;
			private Status status;

			@Getter
			@Setter
			@ToString
			public static class Status {
				private String disabled;
				private String alreadyRegistered;
				private String mismatch;
				private String noPending;
			}
		}

		@Getter
		@Setter
		@ToString
		public static class Authentication {
			private List<String> prompt;
			private Bruteforce bruteforce;
			private Verification verification;
			private Status status;

			@Getter
			@Setter
			@ToString
			public static class Status {
				private String invalid;
				private String notRegistered;
				private String noPending;
			}

			@Getter
			@Setter
			@ToString
			public static class Bruteforce {
				private List<String> exceeded;
				private List<String> remaining;
			}

			@Getter
			@Setter
			@ToString
			public static class Verification {
				private List<String> prompt;
				private String required;
				private String unavailable;
			}
		}
	}

	@Getter
	@Setter
	@ToString
	public static class Password {
		private String tooShort;
		private String tooLong;
		private String noSpaces;
		private String missingUpper;
		private String missingLower;
		private String missingNumber;
		private String missingSpecial;
	}

	@Getter
	@Setter
	@ToString
	public static class ChangePassword {
		private String success;
		private String mismatch;
		private String invalidCurrent;
		private String notLoggedIn;
	}

	@Getter
	@Setter
	@ToString
	public static class Commands {
		private Credential credential;
		private Admin admin;

		@Getter
		@Setter
		@ToString
		public static class Credential {
			private List<String> confirm;
			private List<String> confirmed;
			private String verificationRequired;
			private String cancelled;
			private String expired;
			private String noPending;
			private String pendingExists;
			private String alreadyPrimary;
		}

		@Getter
		@Setter
		@ToString
		public static class Admin {
			private String registered;
			private String deleted;
			private String passwordSet;
			private String notFound;
			private String alreadyRegistered;

			/**
			 * Message shown when {@code /identica credential register} names a username an account holds already,
			 * in any case and through any provider, or whose Credential subject belongs to an account. Nothing is
			 * registered or linked.
			 * Placeholders:
			 * - {target}: the username the command named
			 * - {username}: the existing account's username
			 * - {uniqueId}: the existing account's UUID
			 */
			private String accountExists;

			/**
			 * Message shown when {@code /identica credential register} would give the new account the UUID reserved
			 * for its username, but another account has that UUID already. Nothing is registered.
			 * Placeholders:
			 * - {target}: the username the command named
			 * - {uniqueId}: the reserved UUID
			 * - {username}: the username of the account that has the UUID
			 */
			private String reservationTaken;
		}
	}
}
