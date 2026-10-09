package me.whereareiam.identica.feature.verification.command;

import me.whereareiam.identica.command.SessionBoundCommand;
import me.whereareiam.identica.feature.verification.VerificationService;
import me.whereareiam.identica.feature.verification.model.challenge.VerificationChallengeResult;
import me.whereareiam.identica.feature.verification.model.interaction.CodeVerificationInteraction;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionRequest;
import me.whereareiam.identica.feature.verification.model.resolution.VerificationResolutionResult;
import me.whereareiam.identica.feature.verification.type.status.VerificationChallengeStatus;
import me.whereareiam.identica.feature.verification.type.status.VerificationResolutionStatus;
import me.whereareiam.identica.model.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Shared step-up verification for commands that require an authenticated session.
 * The current session provider determines whether verification is enabled.
 *
 * @param <T> protected action request type
 */
public abstract class ProtectedActionCommand<T> extends SessionBoundCommand {
	private final @NotNull VerificationService verificationService;

	/**
	 * Uses the compiled verification service, which resolves current-provider policy.
	 *
	 * @param verificationService verification operations
	 */
	protected ProtectedActionCommand(@NotNull VerificationService verificationService) {
		this.verificationService = verificationService;
	}

	protected final boolean requiresStepUp(@NotNull UUID uniqueId) {
		VerificationService service = verificationService;
		if (service.findEnrollments(uniqueId).isEmpty()) return false;
		Session session = sessionService().findByUniqueId(uniqueId).join().orElse(null);
		return session == null || session.getProviderId() == null || service.isEnabledForProvider(session.getProviderId());
	}

	protected final @NotNull StepUpPreparation prepareStepUp(
			@NotNull UUID uniqueId,
			@Nullable String purpose
	) {
		Session session = sessionService().findByUniqueId(uniqueId).join().orElse(null);
		if (session == null || session.getProviderId() == null || session.getProviderId().isBlank())
			return StepUpPreparation.currentSessionRequired();

		VerificationResolutionResult resolution = verificationService.resolveVerification(
				VerificationResolutionRequest.builder()
						.uniqueId(uniqueId)
						.providerId(session.getProviderId())
						.purpose(purpose)
						.build()
		);

		if (resolution.getStatus() == VerificationResolutionStatus.WAITING
				|| resolution.getStatus() == VerificationResolutionStatus.SATISFIED)
			return StepUpPreparation.ready();

		return StepUpPreparation.selectionRequired();
	}

	protected final @NotNull StepUpResult confirmStepUp(
			@NotNull UUID uniqueId,
			@NotNull String input,
			@Nullable String purpose
	) {
		Session session = sessionService().findByUniqueId(uniqueId).join().orElse(null);
		if (session == null || session.getProviderId() == null || session.getProviderId().isBlank())
			return StepUpResult.currentSessionRequired();

		VerificationChallengeResult<?> attempt = verificationService.submitChallenge(
				uniqueId,
				session.getProviderId(),
				purpose,
				CodeVerificationInteraction.builder()
						.subjectUniqueId(uniqueId)
						.code(input)
						.build()
		);
		if (attempt.getStatus() == VerificationChallengeStatus.METHOD_NOT_SELECTED)
			return StepUpResult.selectionRequired();

		if (attempt.getStatus() != VerificationChallengeStatus.VERIFIED)
			return StepUpResult.invalidCode();

		return StepUpResult.verified();
	}

	/** Outcome of submitting a protected-action verification code. */
	public static final class StepUpResult {
		/** Session, selection, and verification outcomes exposed to command handlers. */
		public enum Status {
			INVALID_CODE,
			CURRENT_SESSION_REQUIRED,
			SELECTION_REQUIRED,
			VERIFIED
		}

		private final Status status;

		private StepUpResult(@NotNull Status status) {
			this.status = status;
		}

		/** @return a result indicating that the submitted code was not verified */
		public static @NotNull StepUpResult invalidCode() {
			return new StepUpResult(Status.INVALID_CODE);
		}

		/** @return a result requiring an authenticated session */
		public static @NotNull StepUpResult currentSessionRequired() {
			return new StepUpResult(Status.CURRENT_SESSION_REQUIRED);
		}

		/** @return a result requiring a selected verification method */
		public static @NotNull StepUpResult selectionRequired() {
			return new StepUpResult(Status.SELECTION_REQUIRED);
		}

		/** @return a result permitting the protected action */
		public static @NotNull StepUpResult verified() {
			return new StepUpResult(Status.VERIFIED);
		}

		/** @return the outcome for command-specific handling */
		public @NotNull Status getStatus() {
			return status;
		}
	}

	/** Outcome of preparing a challenge against the current session provider. */
	public static final class StepUpPreparation {
		/** Session, selection, and verification outcomes exposed to command handlers. */
		public enum Status {
			CURRENT_SESSION_REQUIRED,
			SELECTION_REQUIRED,
			READY
		}

		private final Status status;

		private StepUpPreparation(@NotNull Status status) {
			this.status = status;
		}

		/** @return a preparation requiring an authenticated session */
		public static @NotNull StepUpPreparation currentSessionRequired() {
			return new StepUpPreparation(Status.CURRENT_SESSION_REQUIRED);
		}

		/** @return a preparation requiring a selected verification method */
		public static @NotNull StepUpPreparation selectionRequired() {
			return new StepUpPreparation(Status.SELECTION_REQUIRED);
		}

		/** @return a preparation whose challenge is waiting or already satisfied */
		public static @NotNull StepUpPreparation ready() {
			return new StepUpPreparation(Status.READY);
		}

		/** @return the outcome for command-specific handling */
		public @NotNull Status getStatus() {
			return status;
		}
	}
}
