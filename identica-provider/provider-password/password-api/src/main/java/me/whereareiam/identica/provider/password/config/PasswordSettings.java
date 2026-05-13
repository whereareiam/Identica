package me.whereareiam.identica.provider.password.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.configura.ConfigDocument;

import java.time.Duration;

@Getter
@Setter
@ToString
public class PasswordSettings extends ConfigDocument {
	private Scenario scenario;
	private Replication replication;
	private Cryptography cryptography;

	@Getter
	@Setter
	@ToString
	public static class Scenario {
		private Registration registration;
		private Authentication authentication;
		private ChangePassword changePassword;

		@Getter
		@Setter
		@ToString
		public static class Registration {
			private boolean enabled;
			private boolean requireRepeat;
			private Username username;
			private Password password;

			@Getter
			@Setter
			@ToString
			public static class Username {
				private int minLength;
				private int maxLength;
				private String pattern;
			}

			@Getter
			@Setter
			@ToString
			public static class Password {
				private int minLength;
				private int maxLength;
				private int minUpper;
				private int minLower;
				private int minNumber;
				private int minSpecial;
			}
		}

		@Getter
		@Setter
		@ToString
		public static class Authentication {
			private Bruteforce bruteforce;

			@Getter
			@Setter
			@ToString
			public static class Bruteforce {
				private int maxAttempts;
				private Lockout lockout;
				private Warning warning;

				@Getter
				@Setter
				@ToString
				public static class Lockout {
					private boolean enabled;
					private Duration duration;
				}

				@Getter
				@Setter
				@ToString
				public static class Warning {
					private boolean enabled;
					private int thresholdPercentage;
				}
			}
		}

		@Getter
		@Setter
		@ToString
		public static class ChangePassword {
			private boolean requireRepeat;
		}
	}

	@Getter
	@Setter
	@ToString
	public static class Replication {
		private Cache cache;

		@Getter
		@Setter
		@ToString
		public static class Cache {
			private String lockout;
		}
	}

	@Getter
	@Setter
	@ToString
	public static class Cryptography {
		private String algorithm;
		private boolean autoupgrade;
		private Algorithms algorithms;

		@Getter
		@Setter
		@ToString
		public static class Algorithms {
			private Bcrypt bcrypt;
			private Argon2 argon2;

			@Getter
			@Setter
			@ToString
			public static class Bcrypt {
				private int cost;
			}

			@Getter
			@Setter
			@ToString
			public static class Argon2 {
				private int iterations;
				private int parallelism;
				private int memoryKb;
			}
		}
	}
}
