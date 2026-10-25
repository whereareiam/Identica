package me.whereareiam.identica.provider.credential;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.feature.verification.VerificationMessageDefinitions;
import me.whereareiam.identica.model.message.MessageDefinition;
import me.whereareiam.identica.model.message.Prompt;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Credential's messages to players and the prompts its steps ask with. Their texts sit in Credential's
 * {@code messages.yml}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class CredentialMessageDefinitions {
	/**
	 * The password of an existing account, to log in or to confirm a move to another provider.
	 */
	public static final Prompt PASSWORD_PROMPT = Prompt.builder()
			.id("credential:password")
			.field(Prompt.Field.of("password", Prompt.FieldType.SECRET))
			.command("login")
			.build();

	/**
	 * A new password, to register or to move an account to Credential.
	 */
	public static final Prompt NEW_PASSWORD_PROMPT = Prompt.builder()
			.id("credential:new-password")
			.field(Prompt.Field.of("password", Prompt.FieldType.SECRET))
			.command("pass")
			.build();

	/**
	 * The new password once more, to confirm it.
	 */
	public static final Prompt REPEAT_PASSWORD_PROMPT = Prompt.builder()
			.id("credential:repeat-password")
			.field(Prompt.Field.of("password", Prompt.FieldType.SECRET))
			.command("passconfirm")
			.build();

	/**
	 * Asks for the account's password to log in.
	 */
	public static final MessageDefinition<CredentialMessages> PASSWORD = MessageDefinition.<CredentialMessages>builder()
			.id("credential:password")
			.text(messages -> MessageDefinition.lines(messages.getScenario().getAuthentication().getPrompt()))
			.prompt(PASSWORD_PROMPT)
			.build();

	/**
	 * The password was wrong; asks again.
	 */
	public static final MessageDefinition<CredentialMessages> PASSWORD_INVALID = MessageDefinition.<CredentialMessages>builder()
			.id("credential:password-invalid")
			.text(messages -> messages.getScenario().getAuthentication().getStatus().getInvalid())
			.prompt(PASSWORD_PROMPT)
			.build();

	/**
	 * The player has no password account to log in to.
	 */
	public static final MessageDefinition<CredentialMessages> NOT_REGISTERED = MessageDefinition.<CredentialMessages>builder()
			.id("credential:not-registered")
			.text(messages -> messages.getScenario().getAuthentication().getStatus().getNotRegistered())
			.build();

	/**
	 * The player typed a password while no login waited for one.
	 */
	public static final MessageDefinition<CredentialMessages> NO_PENDING_LOGIN = MessageDefinition.<CredentialMessages>builder()
			.id("credential:no-pending-login")
			.text(messages -> messages.getScenario().getAuthentication().getStatus().getNoPending())
			.build();

	/**
	 * Too many wrong passwords; placeholder {@code seconds}, how long the lockout lasts.
	 */
	public static final MessageDefinition<CredentialMessages> BRUTEFORCE_EXCEEDED = MessageDefinition.<CredentialMessages>builder()
			.id("credential:bruteforce-exceeded")
			.text(messages -> MessageDefinition.lines(messages.getScenario().getAuthentication().getBruteforce().getExceeded()))
			.placeholder("seconds")
			.build();

	/**
	 * Warns how many wrong passwords are left; placeholder {@code remaining}.
	 */
	public static final MessageDefinition<CredentialMessages> BRUTEFORCE_REMAINING = MessageDefinition.<CredentialMessages>builder()
			.id("credential:bruteforce-remaining")
			.text(messages -> MessageDefinition.lines(messages.getScenario().getAuthentication().getBruteforce().getRemaining()))
			.placeholder("remaining")
			.build();

	/**
	 * Asks for the code of the account's authenticator app.
	 */
	public static final MessageDefinition<CredentialMessages> VERIFICATION_CODE = MessageDefinition.<CredentialMessages>builder()
			.id("credential:verification-code")
			.text(messages -> MessageDefinition.lines(messages.getScenario().getAuthentication().getVerification().getPrompt()))
			.prompt(VerificationMessageDefinitions.CODE_PROMPT)
			.build();

	/**
	 * The account needs a verified code before the login completes, but has no method to give one.
	 */
	public static final MessageDefinition<CredentialMessages> VERIFICATION_REQUIRED = MessageDefinition.<CredentialMessages>builder()
			.id("credential:verification-required")
			.text(messages -> messages.getScenario().getAuthentication().getVerification().getRequired())
			.build();

	/**
	 * The account's verification cannot be used right now.
	 */
	public static final MessageDefinition<CredentialMessages> VERIFICATION_UNAVAILABLE = MessageDefinition.<CredentialMessages>builder()
			.id("credential:verification-unavailable")
			.text(messages -> messages.getScenario().getAuthentication().getVerification().getUnavailable())
			.build();

	/**
	 * Asks for a new password to register.
	 */
	public static final MessageDefinition<CredentialMessages> NEW_PASSWORD = MessageDefinition.<CredentialMessages>builder()
			.id("credential:new-password")
			.text(messages -> MessageDefinition.lines(messages.getScenario().getRegistration().getPrompt()))
			.prompt(NEW_PASSWORD_PROMPT)
			.build();

	/**
	 * Asks for the new password once more.
	 */
	public static final MessageDefinition<CredentialMessages> REPEAT_PASSWORD = MessageDefinition.<CredentialMessages>builder()
			.id("credential:repeat-password")
			.text(messages -> MessageDefinition.lines(messages.getScenario().getRegistration().getConfirmPrompt()))
			.prompt(REPEAT_PASSWORD_PROMPT)
			.build();

	/**
	 * Registration with a password is turned off.
	 */
	public static final MessageDefinition<CredentialMessages> REGISTRATION_DISABLED = MessageDefinition.<CredentialMessages>builder()
			.id("credential:registration-disabled")
			.text(messages -> messages.getScenario().getRegistration().getStatus().getDisabled())
			.build();

	/**
	 * The name is already registered; asks for its password.
	 */
	public static final MessageDefinition<CredentialMessages> ALREADY_REGISTERED = MessageDefinition.<CredentialMessages>builder()
			.id("credential:already-registered")
			.text(messages -> messages.getScenario().getRegistration().getStatus().getAlreadyRegistered())
			.prompt(PASSWORD_PROMPT)
			.build();

	/**
	 * The repeated password differs; asks for a new password again.
	 */
	public static final MessageDefinition<CredentialMessages> PASSWORDS_DIFFER = MessageDefinition.<CredentialMessages>builder()
			.id("credential:passwords-differ")
			.text(messages -> messages.getScenario().getRegistration().getStatus().getMismatch())
			.prompt(NEW_PASSWORD_PROMPT)
			.build();

	/**
	 * The player repeated a password before choosing one; asks for a new password.
	 */
	public static final MessageDefinition<CredentialMessages> NO_NEW_PASSWORD = MessageDefinition.<CredentialMessages>builder()
			.id("credential:no-new-password")
			.text(messages -> messages.getScenario().getRegistration().getStatus().getNoPending())
			.prompt(NEW_PASSWORD_PROMPT)
			.build();

	/**
	 * Asks for the account's password to move it to another provider.
	 */
	public static final MessageDefinition<CredentialMessages> MIGRATION_PASSWORD = MessageDefinition.<CredentialMessages>builder()
			.id("credential:migration-password")
			.text(messages -> MessageDefinition.lines(messages.getScenario().getMigration().getVerification().getPrompt()))
			.prompt(PASSWORD_PROMPT)
			.build();

	/**
	 * The password for the move was wrong; asks again.
	 */
	public static final MessageDefinition<CredentialMessages> MIGRATION_PASSWORD_INVALID = MessageDefinition.<CredentialMessages>builder()
			.id("credential:migration-password-invalid")
			.text(messages -> messages.getScenario().getMigration().getVerification().getStatus().getInvalid())
			.prompt(PASSWORD_PROMPT)
			.build();

	/**
	 * Asks for a password for the account moving to Credential.
	 */
	public static final MessageDefinition<CredentialMessages> MIGRATION_NEW_PASSWORD = MessageDefinition.<CredentialMessages>builder()
			.id("credential:migration-new-password")
			.text(messages -> MessageDefinition.lines(messages.getScenario().getMigration().getSetup().getPrompt()))
			.prompt(NEW_PASSWORD_PROMPT)
			.build();

	/**
	 * Asks for the moving account's new password once more.
	 */
	public static final MessageDefinition<CredentialMessages> MIGRATION_REPEAT_PASSWORD = MessageDefinition.<CredentialMessages>builder()
			.id("credential:migration-repeat-password")
			.text(messages -> MessageDefinition.lines(messages.getScenario().getMigration().getSetup().getConfirmPrompt()))
			.prompt(REPEAT_PASSWORD_PROMPT)
			.build();

	/**
	 * Moving accounts to Credential is turned off.
	 */
	public static final MessageDefinition<CredentialMessages> MIGRATION_DISABLED = MessageDefinition.<CredentialMessages>builder()
			.id("credential:migration-disabled")
			.text(messages -> messages.getScenario().getMigration().getSetup().getStatus().getDisabled())
			.build();

	/**
	 * The repeated password of the move differs; asks for a new password again.
	 */
	public static final MessageDefinition<CredentialMessages> MIGRATION_PASSWORDS_DIFFER = MessageDefinition.<CredentialMessages>builder()
			.id("credential:migration-passwords-differ")
			.text(messages -> messages.getScenario().getMigration().getSetup().getStatus().getMismatch())
			.prompt(NEW_PASSWORD_PROMPT)
			.build();

	/**
	 * The player repeated a password before choosing one for the move.
	 */
	public static final MessageDefinition<CredentialMessages> MIGRATION_NO_NEW_PASSWORD = MessageDefinition.<CredentialMessages>builder()
			.id("credential:migration-no-new-password")
			.text(messages -> messages.getScenario().getMigration().getSetup().getStatus().getNoPending())
			.prompt(NEW_PASSWORD_PROMPT)
			.build();

	/**
	 * The new password is shorter than allowed.
	 */
	public static final MessageDefinition<CredentialMessages> PASSWORD_TOO_SHORT = MessageDefinition.<CredentialMessages>builder()
			.id("credential:password-too-short")
			.text(messages -> messages.getPassword().getTooShort())
			.build();

	/**
	 * The new password is longer than allowed.
	 */
	public static final MessageDefinition<CredentialMessages> PASSWORD_TOO_LONG = MessageDefinition.<CredentialMessages>builder()
			.id("credential:password-too-long")
			.text(messages -> messages.getPassword().getTooLong())
			.build();

	/**
	 * The new password contains spaces.
	 */
	public static final MessageDefinition<CredentialMessages> PASSWORD_HAS_SPACES = MessageDefinition.<CredentialMessages>builder()
			.id("credential:password-has-spaces")
			.text(messages -> messages.getPassword().getNoSpaces())
			.build();

	/**
	 * The new password has too few uppercase letters.
	 */
	public static final MessageDefinition<CredentialMessages> PASSWORD_MISSING_UPPER = MessageDefinition.<CredentialMessages>builder()
			.id("credential:password-missing-upper")
			.text(messages -> messages.getPassword().getMissingUpper())
			.build();

	/**
	 * The new password has too few lowercase letters.
	 */
	public static final MessageDefinition<CredentialMessages> PASSWORD_MISSING_LOWER = MessageDefinition.<CredentialMessages>builder()
			.id("credential:password-missing-lower")
			.text(messages -> messages.getPassword().getMissingLower())
			.build();

	/**
	 * The new password has too few digits.
	 */
	public static final MessageDefinition<CredentialMessages> PASSWORD_MISSING_NUMBER = MessageDefinition.<CredentialMessages>builder()
			.id("credential:password-missing-number")
			.text(messages -> messages.getPassword().getMissingNumber())
			.build();

	/**
	 * The new password has too few special characters.
	 */
	public static final MessageDefinition<CredentialMessages> PASSWORD_MISSING_SPECIAL = MessageDefinition.<CredentialMessages>builder()
			.id("credential:password-missing-special")
			.text(messages -> messages.getPassword().getMissingSpecial())
			.build();

	/**
	 * The password was changed.
	 */
	public static final MessageDefinition<CredentialMessages> CHANGE_PASSWORD_SUCCESS = MessageDefinition.<CredentialMessages>builder()
			.id("credential:change-password-success")
			.text(messages -> messages.getChangePassword().getSuccess())
			.build();

	/**
	 * The new passwords differ.
	 */
	public static final MessageDefinition<CredentialMessages> CHANGE_PASSWORD_MISMATCH = MessageDefinition.<CredentialMessages>builder()
			.id("credential:change-password-mismatch")
			.text(messages -> messages.getChangePassword().getMismatch())
			.build();

	/**
	 * The current password was wrong.
	 */
	public static final MessageDefinition<CredentialMessages> CHANGE_PASSWORD_INVALID_CURRENT = MessageDefinition.<CredentialMessages>builder()
			.id("credential:change-password-invalid-current")
			.text(messages -> messages.getChangePassword().getInvalidCurrent())
			.build();

	/**
	 * Changing the password needs a login first.
	 */
	public static final MessageDefinition<CredentialMessages> CHANGE_PASSWORD_NOT_LOGGED_IN = MessageDefinition.<CredentialMessages>builder()
			.id("credential:change-password-not-logged-in")
			.text(messages -> messages.getChangePassword().getNotLoggedIn())
			.build();

	/**
	 * Asks the player to confirm moving their account to Credential.
	 */
	public static final MessageDefinition<CredentialMessages> MOVE_CONFIRM = MessageDefinition.<CredentialMessages>builder()
			.id("credential:move-confirm")
			.text(messages -> MessageDefinition.lines(messages.getCommands().getCredential().getConfirm()))
			.build();

	/**
	 * The move to Credential was confirmed.
	 */
	public static final MessageDefinition<CredentialMessages> MOVE_CONFIRMED = MessageDefinition.<CredentialMessages>builder()
			.id("credential:move-confirmed")
			.text(messages -> MessageDefinition.lines(messages.getCommands().getCredential().getConfirmed()))
			.build();

	/**
	 * The move needs a verified code first.
	 */
	public static final MessageDefinition<CredentialMessages> MOVE_VERIFICATION_REQUIRED = MessageDefinition.<CredentialMessages>builder()
			.id("credential:move-verification-required")
			.text(messages -> messages.getCommands().getCredential().getVerificationRequired())
			.build();

	/**
	 * The move was cancelled.
	 */
	public static final MessageDefinition<CredentialMessages> MOVE_CANCELLED = MessageDefinition.<CredentialMessages>builder()
			.id("credential:move-cancelled")
			.text(messages -> messages.getCommands().getCredential().getCancelled())
			.build();

	/**
	 * The move waited too long.
	 */
	public static final MessageDefinition<CredentialMessages> MOVE_EXPIRED = MessageDefinition.<CredentialMessages>builder()
			.id("credential:move-expired")
			.text(messages -> messages.getCommands().getCredential().getExpired())
			.build();

	/**
	 * No move waits to be confirmed.
	 */
	public static final MessageDefinition<CredentialMessages> MOVE_NO_PENDING = MessageDefinition.<CredentialMessages>builder()
			.id("credential:move-no-pending")
			.text(messages -> messages.getCommands().getCredential().getNoPending())
			.build();

	/**
	 * A move already waits.
	 */
	public static final MessageDefinition<CredentialMessages> MOVE_PENDING_EXISTS = MessageDefinition.<CredentialMessages>builder()
			.id("credential:move-pending-exists")
			.text(messages -> messages.getCommands().getCredential().getPendingExists())
			.build();

	/**
	 * The account already uses Credential.
	 */
	public static final MessageDefinition<CredentialMessages> MOVE_ALREADY_PRIMARY = MessageDefinition.<CredentialMessages>builder()
			.id("credential:move-already-primary")
			.text(messages -> messages.getCommands().getCredential().getAlreadyPrimary())
			.build();

	/**
	 * Shown after a returning player was recognised.
	 */
	public static final MessageDefinition<CredentialMessages> COMPLETION_RECOGNITION = MessageDefinition.<CredentialMessages>builder()
			.id("credential:completion-recognition")
			.text(messages -> MessageDefinition.lines(messages.getCompletion().getRecognition().getBody()))
			.placeholder("player")
			.placeholder("provider")
			.build();

	/**
	 * Shown after a login with a password.
	 */
	public static final MessageDefinition<CredentialMessages> COMPLETION_AUTHENTICATION = MessageDefinition.<CredentialMessages>builder()
			.id("credential:completion-authentication")
			.text(messages -> MessageDefinition.lines(messages.getCompletion().getAuthentication().getBody()))
			.placeholder("player")
			.placeholder("provider")
			.build();

	/**
	 * Shown after a registration.
	 */
	public static final MessageDefinition<CredentialMessages> COMPLETION_REGISTRATION = MessageDefinition.<CredentialMessages>builder()
			.id("credential:completion-registration")
			.text(messages -> MessageDefinition.lines(messages.getCompletion().getRegistration().getBody()))
			.placeholder("player")
			.placeholder("provider")
			.build();

	/**
	 * Shown after an account moved to Credential.
	 */
	public static final MessageDefinition<CredentialMessages> COMPLETION_MIGRATION = MessageDefinition.<CredentialMessages>builder()
			.id("credential:completion-migration")
			.text(messages -> MessageDefinition.lines(messages.getCompletion().getMigration().getBody()))
			.placeholder("player")
			.placeholder("provider")
			.build();

	/**
	 * Returns every message of Credential to players.
	 *
	 * @return the definitions
	 */
	public static @NotNull List<MessageDefinition<CredentialMessages>> all() {
		return List.of(
				PASSWORD,
				PASSWORD_INVALID,
				NOT_REGISTERED,
				NO_PENDING_LOGIN,
				BRUTEFORCE_EXCEEDED,
				BRUTEFORCE_REMAINING,
				VERIFICATION_CODE,
				VERIFICATION_REQUIRED,
				VERIFICATION_UNAVAILABLE,
				NEW_PASSWORD,
				REPEAT_PASSWORD,
				REGISTRATION_DISABLED,
				ALREADY_REGISTERED,
				PASSWORDS_DIFFER,
				NO_NEW_PASSWORD,
				MIGRATION_PASSWORD,
				MIGRATION_PASSWORD_INVALID,
				MIGRATION_NEW_PASSWORD,
				MIGRATION_REPEAT_PASSWORD,
				MIGRATION_DISABLED,
				MIGRATION_PASSWORDS_DIFFER,
				MIGRATION_NO_NEW_PASSWORD,
				PASSWORD_TOO_SHORT,
				PASSWORD_TOO_LONG,
				PASSWORD_HAS_SPACES,
				PASSWORD_MISSING_UPPER,
				PASSWORD_MISSING_LOWER,
				PASSWORD_MISSING_NUMBER,
				PASSWORD_MISSING_SPECIAL,
				CHANGE_PASSWORD_SUCCESS,
				CHANGE_PASSWORD_MISMATCH,
				CHANGE_PASSWORD_INVALID_CURRENT,
				CHANGE_PASSWORD_NOT_LOGGED_IN,
				MOVE_CONFIRM,
				MOVE_CONFIRMED,
				MOVE_VERIFICATION_REQUIRED,
				MOVE_CANCELLED,
				MOVE_EXPIRED,
				MOVE_NO_PENDING,
				MOVE_PENDING_EXISTS,
				MOVE_ALREADY_PRIMARY,
				COMPLETION_RECOGNITION,
				COMPLETION_AUTHENTICATION,
				COMPLETION_REGISTRATION,
				COMPLETION_MIGRATION
		);
	}
}
