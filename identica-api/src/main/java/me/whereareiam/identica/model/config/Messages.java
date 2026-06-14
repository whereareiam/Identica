package me.whereareiam.identica.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.commandant.model.message.ExceptionMessages;
import me.whereareiam.commandant.model.message.HelpMessages;
import me.whereareiam.commandant.model.message.PaginationMessages;
import me.whereareiam.configura.ConfigDocument;
import me.whereareiam.identica.model.config.type.DateTimePattern;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Root message configuration model.
 */
@Getter
@Setter
@ToString
public class Messages extends ConfigDocument {
	private @NotNull String prefix;
	private @NotNull Format format;
	private @NotNull Commands commands;
	private @NotNull Providers providers;
	private @NotNull Engine engine;
	private @NotNull Routing routing;
	private @NotNull Scenarios scenarios;

	@Getter
	@Setter
	@ToString
	public static class Format {
		private @NotNull Temporal temporal;

		@Getter
		@Setter
		@ToString
		public static class Temporal {
			/**
			 * Formatter pattern used for date-only placeholders.
			 */
			@JsonProperty("date")
			private @NotNull DateTimePattern date;

			/**
			 * Formatter pattern used for date-time placeholders.
			 */
			@JsonProperty("dateTime")
			private @NotNull DateTimePattern dateTime;
		}
	}

	@Getter
	@Setter
	@ToString
	public static class Commands {
		private @NotNull String currentSessionRequired;
		private @NotNull ExceptionMessages exceptions;
		private @NotNull PaginationMessages pagination;
		private @NotNull HelpMessages help;
		private @NotNull Enroll enroll;
		private @NotNull Availability availability;

		private @NotNull Migration migration;
		private @NotNull Reload reload;
		private @NotNull Admin admin;

		/**
		 * Configuration for enroll command messages.
		 */
		@Getter
		@Setter
		@ToString
		public static class Enroll {
			/**
			 * Message shown when no pending enrollment is found.
			 */
			private @NotNull String noPending;

			/**
			 * Message shown when enrollment completes successfully.
			 */
			private @NotNull String completed;
		}

		/**
		 * Configuration for username availability command messages.
		 */
		@Getter
		@Setter
		@ToString
		public static class Availability {
			/**
			 * Username availability messages.
			 */
			private @NotNull Username username;

			/**
			 * Configuration for username availability responses.
			 */
			@Getter
			@Setter
			@ToString
			public static class Username {
				/**
				 * Message shown when the username is available.
				 */
				private @NotNull String free;

				/**
				 * Message shown when the username is already taken.
				 */
				private @NotNull String taken;
			}
		}

		/**
		 * Configuration for migration command messages.
		 */
		@Getter
		@Setter
		@ToString
		public static class Migration {
			private @NotNull Listing list;
			private @NotNull Links links;
			private @NotNull Start start;
			private @NotNull Cancel cancel;
			private @NotNull Primary primary;
			private @NotNull Drop drop;
			private @NotNull String targetNotFound;
			/**
			 * Message shown when migration is locked due to a username conflict.
			 */
			private @NotNull String locked;

			@Getter
			@Setter
			@ToString
			public static class Listing {
				/**
				 * Lines shown in migration list output.
				 * Placeholders:
				 * - {entries}
				 */
				private @NotNull List<String> body;

				/**
				 * Entry format for a single provider link in list output.
				 * Placeholders:
				 * - {providerId}
				 * - {providerName}
				 * - {primary}
				 */
				private @NotNull EntryFormat entry;
			}

			@Getter
			@Setter
			@ToString
			public static class Links {
				private @NotNull String noLinks;
				private @NotNull String notLinked;
			}

			@Getter
			@Setter
			@ToString
			public static class Start {
				private @NotNull String pendingExists;
				private @NotNull String started;
				private @NotNull String providerUnsupported;
				private @NotNull String providerUnavailable;
			}

			@Getter
			@Setter
			@ToString
			public static class Cancel {
				private @NotNull String cancelled;
				private @NotNull String noPending;
			}

			@Getter
			@Setter
			@ToString
			public static class Primary {
				private @NotNull String set;
				private @NotNull String alreadyPrimary;
			}

			@Getter
			@Setter
			@ToString
			public static class Drop {
				private @NotNull String dropped;
				private @NotNull String primaryDenied;
				private @NotNull String lastLinkDenied;
			}
		}

		@Getter
		@Setter
		@ToString
		public static class Admin {
			private @NotNull Clear clear;
			private @NotNull Delete delete;
			private @NotNull Reservation reservation;
			private @NotNull Sessions sessions;

			@Getter
			@Setter
			@ToString
			public static class Clear {
				/**
				 * Confirmation message shown before clearing.
				 * Placeholders:
				 * - {prefix}: The global message prefix
				 * - {target}: Provided input
				 * - {scope}: clear scope (cache/all)
				 * - {uniqueId}: Identica UUID
				 */
				private @NotNull List<String> confirm;

				/**
				 * Message shown when no pending clear exists.
				 */
				private @NotNull String noPending;

				/**
				 * Message shown when pending clear expired.
				 */
				private @NotNull String expired;

				/**
				 * Message shown when clear is cancelled.
				 */
				private @NotNull String cancelled;

				/**
				 * Message shown when no account could be resolved.
				 */
				private @NotNull String notFound;

				private @NotNull Multiple multiple;

				/**
				 * Message shown on successful clear.
				 * Placeholders:
				 * - {scope}
				 * - {uniqueId}
				 */
				private @NotNull String success;

				/**
				 * Message shown when a clear fails.
				 * Placeholders:
				 * - {error}
				 */
				private @NotNull String error;

				/**
				 * Message sent to the target when disconnected.
				 */
				private @NotNull List<String> disconnect;

				@Getter
				@Setter
				@ToString
				public static class Multiple {
					/**
					 * Lines shown before listing matches.
					 * Placeholders:
					 * - {target}
					 * - {count}
					 * - {entries}
					 */
					private @NotNull List<String> body;

					/**
					 * Entry formats for each match.
					 * Placeholders:
					 * - {username}
					 * - {uniqueId}
					 * - {command}
					 */
					private @NotNull EntryFormat entry;
				}
			}

			@Getter
			@Setter
			@ToString
			public static class Delete {
				/**
				 * Confirmation message shown before deleting.
				 * Placeholders:
				 * - {target}
				 * - {uniqueId}
				 */
				private @NotNull List<String> confirm;

				/**
				 * Message shown when no pending delete exists.
				 */
				private @NotNull String noPending;

				/**
				 * Message shown when pending delete expired.
				 */
				private @NotNull String expired;

				/**
				 * Message shown when delete is cancelled.
				 */
				private @NotNull String cancelled;

				/**
				 * Message shown when no account could be resolved.
				 */
				private @NotNull String notFound;

				private @NotNull Clear.Multiple multiple;

				/**
				 * Message shown on successful delete.
				 * Placeholders:
				 * - {uniqueId}
				 */
				private @NotNull String success;

				/**
				 * Message shown when a delete fails.
				 * Placeholders:
				 * - {error}
				 */
				private @NotNull String error;

				/**
				 * Message sent to the target when disconnected.
				 */
				private @NotNull List<String> disconnect;
			}

			@Getter
			@Setter
			@ToString
			public static class Reservation {
				private @NotNull String set;
				private @NotNull String info;
				private @NotNull String deleted;
				private @NotNull String notFound;
				private @NotNull String invalidKey;
			}

			@Getter
			@Setter
			@ToString
			public static class Sessions {
				/**
				 * Placeholder value used when session fields are missing.
				 */
				private @NotNull String unknown;
				@JsonProperty("list")
				private @NotNull Listing listing;
				private @NotNull Detail status;
				private @NotNull End end;
				private @NotNull Multiple multiple;

				@Getter
				@Setter
				@ToString
				public static class Listing {
					/**
					 * Lines shown in session list output.
					 * Placeholders:
					 * - {entries}
					 */
					private @NotNull List<String> body;

					/**
					 * Entry format for a single session in list output.
					 * Placeholders:
					 * - {username}
					 * - {uniqueId}
					 * - {eligibility}
					 * - {session}
					 * - {ip}
					 */
					private @NotNull EntryFormat entry;

					/**
					 * Message shown when no sessions are available.
					 */
					private @NotNull String empty;
				}

				@Getter
				@Setter
				@ToString
				public static class Detail {
					/**
					 * Detailed session info lines.
					 * Placeholders:
					 * - {username}
					 * - {original}
					 * - {effective}
					 * - {uniqueId}
					 * - {eligibility}
					 * - {subject}
					 * - {session}
					 * - {ip}
					 * - {created} (same as {createdDateTime})
					 * - {createdDate}
					 * - {createdDateTime}
					 */
					private @NotNull List<String> body;

					/**
					 * Message shown when no active session found.
					 * Placeholders:
					 * - {target}
					 */
					private @NotNull String notFound;
				}

				@Getter
				@Setter
				@ToString
				public static class End {
					/**
					 * Message shown on successful session end.
					 * Placeholders:
					 * - {username}
					 * - {uniqueId}
					 */
					private @NotNull String ended;

					/**
					 * Message shown when no active session found.
					 * Placeholders:
					 * - {target}
					 */
					private @NotNull String notFound;

					/**
					 * Message sent to the player when their session ends.
					 */
					private @NotNull List<String> disconnect;
				}

				@Getter
				@Setter
				@ToString
				public static class Multiple {
					/**
					 * Lines shown before listing matches.
					 * Placeholders:
					 * - {target}
					 * - {count}
					 * - {entries}
					 */
					private @NotNull List<String> body;

					/**
					 * Entry formats for each match.
					 * Placeholders:
					 * - {username}
					 * - {uniqueId}
					 * - {command}
					 */
					private @NotNull EntryFormat entry;
				}
			}

		}
		/**
		 * Configuration for reload command messages.
		 */
		@Getter
		@Setter
		@ToString
		public static class Reload {
			/**
			 * Success message when reload completes successfully.
			 * Placeholders:
			 * - {prefix}: The global message prefix
			 */
			private @NotNull String success;

			/**
			 * Error message when reload fails.
			 * Placeholders:
			 * - {prefix}: The global message prefix
			 * - {error}: The error message
			 */
			private @NotNull String error;
		}


		/**
		 * Format definitions for command entry rendering.
		 */
		@Getter
		@Setter
		@ToString
		public static class EntryFormat {
			/**
			 * Format used when all required placeholders are present.
			 */
			private @NotNull String format;
			/**
			 * Format used when one or more placeholders are missing.
			 */
			private @NotNull String emptyFormat;
		}
	}

	@Getter
	@Setter
	@ToString
	public static class Providers {
		private @NotNull List<String> noProvidersAvailable;
		private @NotNull List<String> noProvidersMatched;
	}

	@Getter
	@Setter
	@ToString
	public static class Engine {
		/**
		 * Message shown when an existing session is kicked due to a new login.
		 */
		private @NotNull List<String> concurrentLoginKick;
		/**
		 * Message shown when resume or advance requests are sentineled.
		 */
		private @NotNull List<String> resumeSentineled;
		private @NotNull Journey journey;
		private @NotNull Prepare prepare;

		@Getter
		@Setter
		@ToString
		public static class Prepare {
			private @NotNull List<String> handshakeDenied;
			private @NotNull Errors errors;

			@Getter
			@Setter
			@ToString
			public static class Errors {
				private @NotNull List<String> preparePolicyMissing;
			}
		}

		@Getter
		@Setter
		@ToString
		public static class Journey {
			private @NotNull Stage stage;
			private @NotNull Step step;

			@Getter
			@Setter
			@ToString
			public static class Stage {
				private @NotNull List<String> noCompletion;
			}

			@Getter
			@Setter
			@ToString
			public static class Step {
				private @NotNull List<String> noStatus;
				private @NotNull Enrollment enrollment;

				/**
				 * Enrollment prompt messages.
				 */
				@Getter
				@Setter
				@ToString
				public static class Enrollment {
					private @NotNull List<String> body;
					private @NotNull EntryFormat entryFormat;
					private @NotNull List<String> empty;
					private @NotNull Map<String, String> descriptions;

					/**
					 * Format definitions for enrollment entry rendering.
					 */
					@Getter
					@Setter
					@ToString
					public static class EntryFormat {
						/**
						 * Format used when all required placeholders are present.
						 */
						private @NotNull String format;
						/**
						 * Format used when one or more placeholders are missing.
						 */
						private @NotNull String emptyFormat;
					}
				}
			}
		}
	}

	/**
	 * Routing messages.
	 */
	@Getter
	@Setter
	@ToString
	public static class Routing {
		/**
		 * Message shown when the configured routing target does not exist.
		 *
		 * <p>Placeholders:</p>
		 * <ul>
		 *     <li>{@code {server}}</li>
		 * </ul>
		 */
		private @NotNull List<String> missingServer;
		/**
		 * Message shown when the routing target exists but cannot accept the player.
		 *
		 * <p>{@code message} placeholders:</p>
		 * <ul>
		 *     <li>{@code {server}}</li>
		 *     <li>{@code {serverReason}}</li>
		 * </ul>
		 */
		private @NotNull UnavailableServer unavailableServer;

		@Getter
		@Setter
		@ToString
		public static class UnavailableServer {
			/**
			 * Message shown when the routing target exists but cannot accept the player.
			 *
			 * <p>Placeholders:</p>
			 * <ul>
			 *     <li>{@code {server}}</li>
			 *     <li>{@code {serverReason}}</li>
			 * </ul>
			 */
			private @NotNull List<String> message;
			/**
			 * Fallback text used when the platform does not provide a target failure reason.
			 */
			private @NotNull List<String> fallbackReason;
		}
	}

	@Getter
	@Setter
	@ToString
	public static class Scenarios {
		private @NotNull Authentication authentication;
		private @NotNull Registration registration;
		private @NotNull Migration migration;

		@Getter
		@Setter
		@ToString
		public static class Authentication extends Scenario {
			private @NotNull List<String> authenticationFailed;
			private @NotNull List<String> sessionBuildFailed;
			/**
			 * Message shown when entrypoint selection is required for conflicts.
			 * Placeholders:
			 * - {incomingProvider}
			 * - {existingProvider}
			 * - {incomingProviderId}
			 * - {existingProviderId}
			 * - {incomingHost}
			 * - {existingHost}
			 */
			private @NotNull List<String> conflictEntrypointRequired;
		}

		@Getter
		@Setter
		@ToString
		public static class Registration extends Scenario {
			private @NotNull List<String> registrationFailed;
			private @NotNull List<String> accountAlreadyExists;
		}

		@Getter
		@Setter
		@ToString
		public static class Migration extends Scenario {
			private @NotNull List<String> migrationFailed;
			private @NotNull List<String> cancelled;
		}

		@Getter
		@Setter
		@ToString
		public static class Scenario {
			/**
			 * Message shown when a pipeline attempt is rejected due to concurrency.
			 */
			private @NotNull List<String> pipelineKick;
			/**
			 * Message shown when a pending pipeline expires.
			 */
			private @NotNull List<String> pipelineExpired;
			/**
			 * Messages shown when an advance request is blocked by a pending lock.
			 */
			private @NotNull AdvanceBusy advanceBusy;
			/**
			 * Message shown when the pipeline finishes without a completion result.
			 */
			private @NotNull List<String> noCompletionPipeline;
			private @NotNull ScenarioRouting routing;
			private @NotNull Errors errors;

			@Getter
			@Setter
			@ToString
			public static class AdvanceBusy {
				/**
				 * Chat message shown when an advance request is blocked by a pending lock.
				 */
				private @NotNull String chat;
				/**
				 * Disconnect message shown when an advance request is blocked by a pending lock.
				 */
				private @NotNull List<String> kick;
			}

			@Getter
			@Setter
			@ToString
			public static class Errors {
				private @NotNull List<String> preparationMissingContext;
				private @NotNull List<String> finalizeMissingResult;
				private @NotNull Identity identity;
				private @NotNull Policy policy;
				private @NotNull Session session;
				private @NotNull Journey journey;

				@Getter
				@Setter
				@ToString
				public static class Identity {
					private @NotNull List<String> groupMissingResult;
					private @NotNull List<String> profileMissing;
					private @NotNull List<String> replicationMissing;
					private @NotNull List<String> accountMissing;
					private @NotNull Provider provider;

					@Getter
					@Setter
					@ToString
					public static class Provider {
						private @NotNull List<String> validationMissing;
						private @NotNull List<String> linkMissing;
					}
				}

				@Getter
				@Setter
				@ToString
				public static class Policy {
					private @NotNull List<String> groupMissingResult;
					private @NotNull List<String> accountReviewMissing;
					private @NotNull List<String> ensureNewAccountMissing;
					private @NotNull List<String> accountCreationMissing;
				}

				@Getter
				@Setter
				@ToString
				public static class Session {
					private @NotNull List<String> groupMissingResult;
					private @NotNull List<String> buildMissing;
				}

				@Getter
				@Setter
				@ToString
				public static class Journey {
					private @NotNull List<String> missingResult;
					private @NotNull List<String> missingContext;
					private @NotNull List<String> missingPlan;
				}
			}

			@Getter
			@Setter
			@ToString
			public static class ScenarioRouting {
				/**
				 * Scenario-specific message shown when the configured routing target does not exist.
				 *
				 * <p>Placeholders:</p>
				 * <ul>
				 *     <li>{@code {server}}</li>
				 * </ul>
				 */
				private @NotNull List<String> missingServer;
				/**
				 * Scenario-specific message shown when the routing target exists but cannot accept the player.
				 *
				 * <p>{@code message} placeholders:</p>
				 * <ul>
				 *     <li>{@code {server}}</li>
				 *     <li>{@code {serverReason}}</li>
				 * </ul>
				 */
				private @NotNull UnavailableServer unavailableServer;

				@Getter
				@Setter
				@ToString
				public static class UnavailableServer {
					/**
					 * Scenario-specific message shown when the routing target exists but cannot accept the player.
					 *
					 * <p>Placeholders:</p>
					 * <ul>
					 *     <li>{@code {server}}</li>
					 *     <li>{@code {serverReason}}</li>
					 * </ul>
					 */
					private @NotNull List<String> message;
					/**
					 * Scenario-specific fallback text used when the platform does not provide a target failure reason.
					 */
					private @NotNull List<String> fallbackReason;
				}
			}
		}
	}
}
