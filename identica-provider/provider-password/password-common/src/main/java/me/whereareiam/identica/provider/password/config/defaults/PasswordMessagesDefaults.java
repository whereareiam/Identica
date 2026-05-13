package me.whereareiam.identica.provider.password.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.MergeDefaultsProvider;
import me.whereareiam.identica.provider.password.config.PasswordMessages;

import java.util.List;

@Singleton
public class PasswordMessagesDefaults implements MergeDefaultsProvider<PasswordMessages> {
	@Override
	public PasswordMessages supply(PasswordMessages messages) {
		PasswordMessages.Scenario scenario = new PasswordMessages.Scenario();

		PasswordMessages.Scenario.Registration registration = new PasswordMessages.Scenario.Registration();
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
		PasswordMessages.Scenario.Registration.Status registrationStatus = new PasswordMessages.Scenario.Registration.Status();
		registrationStatus.setSuccess("{prefix}<white>You have been <green>successfully registered</green>.</white>");
		registrationStatus.setDisabled("{prefix}<white>Registration is <red>disabled</red>.</white>");
		registrationStatus.setAlreadyRegistered("{prefix}<white>Your account is already <green>registered</green>.</white>");
		registrationStatus.setMismatch("{prefix}<white>Passwords do not <red>match</red>.</white>");
		registrationStatus.setNoPending("{prefix}<white>No pending registration.</white>");
		registration.setStatus(registrationStatus);
		scenario.setRegistration(registration);

		PasswordMessages.Scenario.Authentication authentication = new PasswordMessages.Scenario.Authentication();
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
		PasswordMessages.Scenario.Authentication.Status authenticationStatus = new PasswordMessages.Scenario.Authentication.Status();
		authenticationStatus.setSuccess("{prefix}<white>Successfully <green>logged in</green>.</white>");
		authenticationStatus.setInvalid("{prefix}<white>Invalid <red>password</red>.</white>");
		authenticationStatus.setNotRegistered("{prefix}<white>No password account found.</white>");
		authenticationStatus.setNoPending("{prefix}<white>No pending login.</white>");
		authentication.setStatus(authenticationStatus);

		PasswordMessages.Scenario.Authentication.Bruteforce bruteforce = new PasswordMessages.Scenario.Authentication.Bruteforce();
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
		PasswordMessages.Scenario.Authentication.Verification verification = new PasswordMessages.Scenario.Authentication.Verification();
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
		messages.setScenario(scenario);

		PasswordMessages.Completion completion = new PasswordMessages.Completion();
		PasswordMessages.Completion.Pipeline sessionCompletion = new PasswordMessages.Completion.Pipeline();
		PasswordMessages.Completion.Pipeline.Title sessionTitle = new PasswordMessages.Completion.Pipeline.Title();
		sessionTitle.setTitle("<gold><bold>Session Restored</bold></gold>");
		sessionTitle.setSubtitle("<dark_gray>Your session was reused.</dark_gray>");
		sessionCompletion.setTitle(sessionTitle);
		sessionCompletion.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Welcome back, <green>{player}</green>.</white>",
				"  <white>Your existing <gold>session</gold> was reused.</white>",
				" ",
				"  <gray>Enjoy your stay.</gray>",
				" "
		));
		completion.setSession(sessionCompletion);

		PasswordMessages.Completion.Pipeline authenticationCompletion = new PasswordMessages.Completion.Pipeline();
		PasswordMessages.Completion.Pipeline.Title authenticationTitle = new PasswordMessages.Completion.Pipeline.Title();
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

		PasswordMessages.Completion.Pipeline registrationCompletion = new PasswordMessages.Completion.Pipeline();
		PasswordMessages.Completion.Pipeline.Title registrationTitle = new PasswordMessages.Completion.Pipeline.Title();
		registrationTitle.setTitle("<gold><bold>Registered</bold></gold>");
		registrationTitle.setSubtitle("<dark_gray>You were registered.</dark_gray>");
		registrationCompletion.setTitle(registrationTitle);
		registrationCompletion.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Welcome, <green>{player}</green>.</white>",
				"  <white>You just registered <gold>via password provider</gold>.</white>",
				"  <white>Please take a moment to read the server rules.</white>",
				" ",
				"  <gray>We hope you enjoy your stay.</gray>",
				" "
		));
		completion.setRegistration(registrationCompletion);

		PasswordMessages.Completion.Pipeline migrationCompletion = new PasswordMessages.Completion.Pipeline();
		PasswordMessages.Completion.Pipeline.Title migrationTitle = new PasswordMessages.Completion.Pipeline.Title();
		migrationTitle.setTitle("<gold><bold>Migrated</bold></gold>");
		migrationTitle.setSubtitle("<dark_gray>You were migrated to password.</dark_gray>");
		migrationCompletion.setTitle(migrationTitle);
		migrationCompletion.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Welcome back, <green>{player}</green>.</white>",
				"  <white>Your account now authenticates <gold>via password provider</gold>.</white>",
				" ",
				"  <gray>You can continue using password login.</gray>",
				" "
		));
		completion.setMigration(migrationCompletion);
		messages.setCompletion(completion);

		PasswordMessages.Password passwordMessages = new PasswordMessages.Password();
		passwordMessages.setTooShort("{prefix}<white>Password is too <red>short</red>.</white>");
		passwordMessages.setTooLong("{prefix}<white>Password is too <red>long</red>.</white>");
		passwordMessages.setNoSpaces("{prefix}<white>Password cannot contain <red>spaces</red>.</white>");
		passwordMessages.setMissingUpper("{prefix}<white>Password needs an <red>uppercase</red> letter.</white>");
		passwordMessages.setMissingLower("{prefix}<white>Password needs a <red>lowercase</red> letter.</white>");
		passwordMessages.setMissingNumber("{prefix}<white>Password needs a <red>number</red>.</white>");
		passwordMessages.setMissingSpecial("{prefix}<white>Password needs a <red>special</red> character.</white>");
		messages.setPassword(passwordMessages);

		PasswordMessages.ChangePassword changePassword = new PasswordMessages.ChangePassword();
		changePassword.setSuccess("{prefix}<white>Password <green>updated</green>.</white>");
		changePassword.setMismatch("{prefix}<white>Passwords do not <red>match</red>.</white>");
		changePassword.setInvalidCurrent("{prefix}<white>Current password is <red>invalid</red>.</white>");
		changePassword.setNotLoggedIn("{prefix}<white>You must be <red>logged in</red> to change password.</white>");
		messages.setChangePassword(changePassword);

		PasswordMessages.Commands commands = new PasswordMessages.Commands();
		PasswordMessages.Commands.Password passwordCommand = new PasswordMessages.Commands.Password();
		passwordCommand.setConfirm(List.of(
				" ",
				" <green><bold>Identica</bold>",
				"  <white>You are about to switch to a <gold>password account</gold>.</white>",
				"  <white>After this, you will log in using a password.</white>",
				" ",
				"  <yellow>/password confirm</yellow> <dark_gray>- <white>Continue migration</white>",
				"  <yellow>/password cancel</yellow> <dark_gray>- <white>Cancel migration</white>",
				" "
		));
		passwordCommand.setConfirmed(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Please rejoin the server to proceed with</white>",
				"<white>migration to the password provider.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		passwordCommand.setVerificationRequired("{prefix}<white>Confirm your verification code with <yellow>/password confirm</yellow> <gray>[Code]</gray> before starting password migration.</white>");
		passwordCommand.setCancelled("{prefix}<white>Password migration cancelled.</white>");
		passwordCommand.setExpired("{prefix}<white>Password migration request expired.</white>");
		passwordCommand.setNoPending("{prefix}<white>No pending password migration.</white>");
		passwordCommand.setPendingExists("{prefix}<white>Password migration already pending.</white>");
		passwordCommand.setAlreadyPrimary("{prefix}<white>Password is already your primary provider.</white>");
		commands.setPassword(passwordCommand);

		PasswordMessages.Commands.Admin admin = new PasswordMessages.Commands.Admin();
		admin.setRegistered("{prefix}<white>Password account <green>registered</green>.</white>");
		admin.setDeleted("{prefix}<white>Password account <green>deleted</green>.</white>");
		admin.setPasswordSet("{prefix}<white>Password <green>updated</green>.</white>");
		admin.setNotFound("{prefix}<white>No password account found.</white>");
		admin.setAlreadyRegistered("{prefix}<white>Password account already <green>registered</green>.</white>");
		commands.setAdmin(admin);

		messages.setCommands(commands);
		return messages;
	}
}
