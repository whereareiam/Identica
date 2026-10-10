package me.whereareiam.identica.provider.credential.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;

import java.util.List;

@Singleton
public class CredentialMessagesDefaults implements DefaultsProvider<CredentialMessages> {
	@Override
	public CredentialMessages supply(CredentialMessages messages) {
		CredentialMessages.Scenario scenario = new CredentialMessages.Scenario();

		CredentialMessages.Scenario.Registration registration = new CredentialMessages.Scenario.Registration();
		registration.setPrompt(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>To create your password account, you have to</white>",
				"  <white>walk through some registration steps.</white>",
				" ",
				"  <gray>Information:",
				"   <gray>6-32 characters, at least 1 uppercase, 1 lowercase,</gray>",
				"   <gray>1 number, and 1 special character.</gray>",
				" ", 
				"  <white>Use <yellow>/pass</yellow> <gray>[Password]</gray> to continue.</white>",
				" "
		));
		registration.setConfirmPrompt(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>To finish your password account registration,</white>",
				"  <white>repeat the same password you entered before.</white>",
				" ",
				"  <white>Use <yellow>/passconfirm</yellow> <gray>[Password]</gray> to continue.</white>",
				" "
		));
		CredentialMessages.Scenario.Registration.Status registrationStatus = new CredentialMessages.Scenario.Registration.Status();
		registrationStatus.setDisabled("{prefix}<white>Registration is <red>disabled</red>.</white>");
		registrationStatus.setAlreadyRegistered("{prefix}<white>Your account is already <green>registered</green>.</white>");
		registrationStatus.setMismatch("{prefix}<white>Passwords do not <red>match</red>.</white>");
		registrationStatus.setNoPending("{prefix}<white>No pending registration.</white>");
		registration.setStatus(registrationStatus);
		scenario.setRegistration(registration);

		CredentialMessages.Scenario.Authentication authentication = new CredentialMessages.Scenario.Authentication();
		authentication.setPrompt(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Welcome back to our server.</white>",
				"  <white>Please log in to proceed.</white>",
				" ",
				"  <white>Use <yellow>/login</yellow> <gray>[Password]</gray> to continue.</white>",
				" "
		));
		CredentialMessages.Scenario.Authentication.Status authenticationStatus = new CredentialMessages.Scenario.Authentication.Status();
		authenticationStatus.setInvalid("{prefix}<white>Invalid <red>password</red>.</white>");
		authenticationStatus.setNotRegistered("{prefix}<white>No password account found.</white>");
		authenticationStatus.setNoPending("{prefix}<white>No pending login.</white>");
		authentication.setStatus(authenticationStatus);

		CredentialMessages.Scenario.Authentication.Bruteforce bruteforce = new CredentialMessages.Scenario.Authentication.Bruteforce();
		bruteforce.setExceeded(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Too many attempts.</white>",
				"<white>Try again in <red>{seconds}s</red>.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		bruteforce.setRemaining(List.of(
				"{prefix}<white>You have <red>{remaining}</red> tries left.</white>"
		));
		authentication.setBruteforce(bruteforce);
		CredentialMessages.Scenario.Authentication.Verification verification = new CredentialMessages.Scenario.Authentication.Verification();
		verification.setPrompt(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Verification required for your account.</white>",
				"  <white>Use <yellow>/2fa confirm</yellow> <gray>[Code]</gray> to continue.</white>",
				" "
		));
		verification.setInvalid("{prefix}<white>Invalid verification <red>code</red>.</white>");
		verification.setRequired("{prefix}<white>A verification method is <red>required</red> before login.</white>");
		verification.setUnavailable("{prefix}<white>Your selected verification method is <red>unavailable</red>.</white>");
		authentication.setVerification(verification);
		scenario.setAuthentication(authentication);

		CredentialMessages.Scenario.Migration migration = new CredentialMessages.Scenario.Migration();

		CredentialMessages.Scenario.Migration.Verification migrationVerification = new CredentialMessages.Scenario.Migration.Verification();
		migrationVerification.setPrompt(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>To migrate this account to the <gold>credential provider</gold>,</white>",
				"  <white>first verify your current password.</white>",
				" ",
				"  <white>Use <yellow>/login</yellow> <gray>[Password]</gray> to continue.</white>",
				" "
		));
		CredentialMessages.Scenario.Migration.Verification.Status migrationVerificationStatus = new CredentialMessages.Scenario.Migration.Verification.Status();
		migrationVerificationStatus.setInvalid("{prefix}<white>Your current password is <red>invalid</red>.</white>");
		migrationVerificationStatus.setNoPending("{prefix}<white>No pending credential migration verification.</white>");
		migrationVerification.setStatus(migrationVerificationStatus);
		migration.setVerification(migrationVerification);

		CredentialMessages.Scenario.Migration.Setup migrationSetup = new CredentialMessages.Scenario.Migration.Setup();
		migrationSetup.setPrompt(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Your account is moving to the credential provider.</white>",
				"  <white>Choose the password you want to sign in with from now on.</white>",
				" ",
				"  <gray>Information:</gray>",
				"   <gray>6-32 characters, at least 1 uppercase, 1 lowercase,</gray>",
				"   <gray>1 number, and 1 special character.</gray>",
				" ",
				"  <white>Use <yellow>/pass</yellow> <gray>[Password]</gray> to continue.</white>",
				" "
		));
		migrationSetup.setConfirmPrompt(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Repeat the same password to finish your</white>",
				"  <white>migration to the credential provider.</white>",
				" ",
				"  <white>Use <yellow>/passconfirm</yellow> <gray>[Password]</gray> to continue.</white>",
				" "
		));
		CredentialMessages.Scenario.Migration.Setup.Status migrationSetupStatus = new CredentialMessages.Scenario.Migration.Setup.Status();
		migrationSetupStatus.setDisabled("{prefix}<white>Credential migration is <red>disabled</red>.</white>");
		migrationSetupStatus.setMismatch("{prefix}<white>Migration passwords do not <red>match</red>.</white>");
		migrationSetupStatus.setNoPending("{prefix}<white>No pending credential migration password step.</white>");
		migrationSetup.setStatus(migrationSetupStatus);
		migration.setSetup(migrationSetup);

		scenario.setMigration(migration);
		messages.setScenario(scenario);

		CredentialMessages.Completion completion = new CredentialMessages.Completion();
		CredentialMessages.Completion.Pipeline recognitionCompletion = new CredentialMessages.Completion.Pipeline();
		CredentialMessages.Completion.Pipeline.Title recognitionTitle = new CredentialMessages.Completion.Pipeline.Title();
		recognitionTitle.setTitle("<gold><bold>Session Restored</bold></gold>");
		recognitionTitle.setSubtitle("<dark_gray>Your session was reused.</dark_gray>");
		recognitionCompletion.setTitle(recognitionTitle);
		recognitionCompletion.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Welcome back, <green>{player}</green>.</white>",
				"  <white>Your existing <gold>session</gold> was reused.</white>",
				" ",
				"  <gray>Enjoy your stay.</gray>",
				" "
		));
		completion.setRecognition(recognitionCompletion);

		CredentialMessages.Completion.Pipeline authenticationCompletion = new CredentialMessages.Completion.Pipeline();
		CredentialMessages.Completion.Pipeline.Title authenticationTitle = new CredentialMessages.Completion.Pipeline.Title();
		authenticationTitle.setTitle("<gold><bold>Signed In</bold></gold>");
		authenticationTitle.setSubtitle("<dark_gray>You were authenticated.</dark_gray>");
		authenticationCompletion.setTitle(authenticationTitle);
		authenticationCompletion.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Welcome back, <green>{player}</green>.</white>",
				"  <white>You were authenticated <gold>via password</gold>.</white>",
				" ",
				"  <gray>Enjoy your stay.</gray>",
				" "
		));
		completion.setAuthentication(authenticationCompletion);

		CredentialMessages.Completion.Pipeline registrationCompletion = new CredentialMessages.Completion.Pipeline();
		CredentialMessages.Completion.Pipeline.Title registrationTitle = new CredentialMessages.Completion.Pipeline.Title();
		registrationTitle.setTitle("<gold><bold>Registered</bold></gold>");
		registrationTitle.setSubtitle("<dark_gray>You were registered.</dark_gray>");
		registrationCompletion.setTitle(registrationTitle);
		registrationCompletion.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Welcome, <green>{player}</green>.</white>",
				"  <white>You just registered <gold>via credential provider</gold>.</white>",
				"  <white>Please take a moment to read the server rules.</white>",
				" ",
				"  <gray>We hope you enjoy your stay.</gray>",
				" "
		));
		completion.setRegistration(registrationCompletion);

		CredentialMessages.Completion.Pipeline migrationCompletion = new CredentialMessages.Completion.Pipeline();
		CredentialMessages.Completion.Pipeline.Title migrationTitle = new CredentialMessages.Completion.Pipeline.Title();
		migrationTitle.setTitle("<gold><bold>Migrated</bold></gold>");
		migrationTitle.setSubtitle("<dark_gray>Credential migration completed.</dark_gray>");
		migrationCompletion.setTitle(migrationTitle);
		migrationCompletion.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Credential provider migration <green>completed</green>.</white>",
				"  <white>Your account now authenticates <gold>via credential provider</gold>.</white>",
				" ",
				"  <gray>Use credential login from now on.</gray>",
				" "
		));
		completion.setMigration(migrationCompletion);
		messages.setCompletion(completion);

		CredentialMessages.Password passwordMessages = new CredentialMessages.Password();
		passwordMessages.setTooShort("{prefix}<white>Credential is too <red>short</red>.</white>");
		passwordMessages.setTooLong("{prefix}<white>Credential is too <red>long</red>.</white>");
		passwordMessages.setNoSpaces("{prefix}<white>Credential cannot contain <red>spaces</red>.</white>");
		passwordMessages.setMissingUpper("{prefix}<white>Credential needs an <red>uppercase</red> letter.</white>");
		passwordMessages.setMissingLower("{prefix}<white>Credential needs a <red>lowercase</red> letter.</white>");
		passwordMessages.setMissingNumber("{prefix}<white>Credential needs a <red>number</red>.</white>");
		passwordMessages.setMissingSpecial("{prefix}<white>Credential needs a <red>special</red> character.</white>");
		messages.setPassword(passwordMessages);

		CredentialMessages.ChangePassword changePassword = new CredentialMessages.ChangePassword();
		changePassword.setSuccess("{prefix}<white>Credential <green>updated</green>.</white>");
		changePassword.setMismatch("{prefix}<white>Passwords do not <red>match</red>.</white>");
		changePassword.setInvalidCurrent("{prefix}<white>Current password is <red>invalid</red>.</white>");
		changePassword.setNotLoggedIn("{prefix}<white>You must be <red>logged in</red> to change password.</white>");
		messages.setChangePassword(changePassword);

		CredentialMessages.Commands commands = new CredentialMessages.Commands();
		CredentialMessages.Commands.Credential credentialCommand = new CredentialMessages.Commands.Credential();
		credentialCommand.setConfirm(List.of(
				" ",
				" <green><bold>Identica</bold>",
				"  <white>You are about to migrate to the <gold>credential provider</gold>.</white>",
				"  <white>After this, this account will authenticate through credentials.</white>",
				" ",
				"  <yellow>/credential confirm</yellow> <dark_gray>- <white>Continue migration</white>",
				"  <yellow>/credential cancel</yellow> <dark_gray>- <white>Cancel migration</white>",
				" "
		));
		credentialCommand.setConfirmed(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Please rejoin the server to proceed with</white>",
				"<white>migration to the credential provider.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		credentialCommand.setVerificationRequired("{prefix}<white>Confirm your verification code with <yellow>/credential confirm</yellow> <gray>[Code]</gray> before starting credential migration.</white>");
		credentialCommand.setCancelled("{prefix}<white>Credential migration cancelled.</white>");
		credentialCommand.setExpired("{prefix}<white>Credential migration request expired.</white>");
		credentialCommand.setNoPending("{prefix}<white>No pending credential migration.</white>");
		credentialCommand.setPendingExists("{prefix}<white>Credential migration already pending.</white>");
		credentialCommand.setAlreadyPrimary("{prefix}<white>Credential is already your primary provider.</white>");
		commands.setCredential(credentialCommand);

		CredentialMessages.Commands.Admin admin = new CredentialMessages.Commands.Admin();
		admin.setRegistered("{prefix}<white>Credential account <green>registered</green>.</white>");
		admin.setDeleted("{prefix}<white>Credential account <green>deleted</green>.</white>");
		admin.setPasswordSet("{prefix}<white>Credential <green>updated</green>.</white>");
		admin.setNotFound("{prefix}<white>No password account found.</white>");
		admin.setAlreadyRegistered("{prefix}<white>Credential account already <green>registered</green>.</white>");
		commands.setAdmin(admin);

		messages.setCommands(commands);
		return messages;
	}
}
