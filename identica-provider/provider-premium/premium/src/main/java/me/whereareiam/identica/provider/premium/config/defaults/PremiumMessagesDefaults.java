package me.whereareiam.identica.provider.premium.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;

import java.util.List;

@Singleton
public class PremiumMessagesDefaults implements DefaultsProvider<PremiumMessages> {
	@Override
	public PremiumMessages supply(PremiumMessages messages) {
		PremiumMessages.Verification verification = new PremiumMessages.Verification();
		verification.setRejoin(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Please rejoin to verify your premium account.",
				"",
				"<gray>If you don't have a premium account, you'll be",
				"<gray>asked to choose a different provider next time.",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		verification.setOwnedUsername(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>This username belongs to a premium account.",
				"",
				"<gray>Log in with that Minecraft account,",
				"<gray>or join with a different username.",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		PremiumMessages.Verification.Authentication authenticationVerification = new PremiumMessages.Verification.Authentication();
		authenticationVerification.setPrompt(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Verification required for your account.</white>",
				"  <white>Use <yellow>/2fa confirm</yellow> <gray>[Code]</gray> to continue.</white>",
				" "
		));
		authenticationVerification.setInvalid("{prefix}<white>Invalid verification <red>code</red>.</white>");
		authenticationVerification.setRequired("{prefix}<white>A verification method is <red>required</red> before login.</white>");
		authenticationVerification.setUnavailable("{prefix}<white>Your selected verification method is <red>unavailable</red>.</white>");
		verification.setAuthentication(authenticationVerification);
		messages.setVerification(verification);

		PremiumMessages.Completion completion = new PremiumMessages.Completion();
		PremiumMessages.Completion.Pipeline recognitionCompletion = new PremiumMessages.Completion.Pipeline();
		PremiumMessages.Completion.Pipeline.Title recognitionTitle = new PremiumMessages.Completion.Pipeline.Title();
		recognitionTitle.setTitle("<gold><bold>Session Restored</bold></gold>");
		recognitionTitle.setSubtitle("<dark_gray>Your premium session was reused.</dark_gray>");
		recognitionCompletion.setTitle(recognitionTitle);
		recognitionCompletion.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Welcome back, <green>{player}</green>.</white>",
				"  <white>Your existing <gold>premium session</gold> was reused.</white>",
				" ",
				"  <gray>No new verification was required.</gray>",
				" "
		));
		completion.setRecognition(recognitionCompletion);

		PremiumMessages.Completion.Pipeline authenticationCompletion = new PremiumMessages.Completion.Pipeline();
		PremiumMessages.Completion.Pipeline.Title authenticationTitle = new PremiumMessages.Completion.Pipeline.Title();
		authenticationTitle.setTitle("<gold><bold>Verified</bold></gold>");
		authenticationTitle.setSubtitle("<dark_gray>You were authenticated.</dark_gray>");
		authenticationCompletion.setTitle(authenticationTitle);
		authenticationCompletion.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Welcome back, <green>{player}</green>.</white>",
				"  <white>You were authenticated <gold>via premium provider</gold>.</white>",
				" ",
				"  <gray>Enjoy your session.</gray>",
				" "
		));
		completion.setAuthentication(authenticationCompletion);

		PremiumMessages.Completion.Pipeline registrationCompletion = new PremiumMessages.Completion.Pipeline();
		PremiumMessages.Completion.Pipeline.Title registrationTitle = new PremiumMessages.Completion.Pipeline.Title();
		registrationTitle.setTitle("<gold><bold>Registered</bold></gold>");
		registrationTitle.setSubtitle("<dark_gray>Your premium account is ready.</dark_gray>");
		registrationCompletion.setTitle(registrationTitle);
		registrationCompletion.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Welcome, <green>{player}</green>.</white>",
				"  <white>Your account was registered <gold>via premium provider</gold>.</white>",
				" ",
				"  <gray>Your premium account will be used for future logins.</gray>",
				" "
		));
		completion.setRegistration(registrationCompletion);

		PremiumMessages.Completion.Pipeline migrationCompletion = new PremiumMessages.Completion.Pipeline();
		PremiumMessages.Completion.Pipeline.Title migrationTitle = new PremiumMessages.Completion.Pipeline.Title();
		migrationTitle.setTitle("<gold><bold>Migrated</bold></gold>");
		migrationTitle.setSubtitle("<dark_gray>Premium migration completed.</dark_gray>");
		migrationCompletion.setTitle(migrationTitle);
		migrationCompletion.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Premium provider migration <green>completed</green>.</white>",
				"  <white>Your account now authenticates <gold>via premium provider</gold>.</white>",
				" ",
				"  <gray>Rejoin with your licensed Minecraft account from now on.</gray>",
				" "
		));
		completion.setMigration(migrationCompletion);
		messages.setCompletion(completion);

		PremiumMessages.Commands commands = new PremiumMessages.Commands();
		PremiumMessages.Commands.Premium premium = new PremiumMessages.Commands.Premium();
		premium.setConfirm(List.of(
				" ",
				" <green><bold>Identica</bold>",
				"  <white>You are about to switch to a <gold>licensed Minecraft account</gold>.</white>",
				"  <white>After this, you will log in with that licensed account.</white>",
				" ",
				"  <yellow>/premium confirm</yellow> <dark_gray>- <white>Continue migration</white>",
				"  <yellow>/premium cancel</yellow> <dark_gray>- <white>Cancel migration</white>",
				" "
		));
		premium.setConfirmed(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Please rejoin the server to proceed with</white>",
				"<white>migration to the premium provider.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		premium.setVerificationRequired("{prefix}<white>Confirm your verification code with <yellow>/premium confirm</yellow> <gray>[Code]</gray> before starting premium migration.</white>");
		premium.setCancelled("{prefix}<white>Premium migration cancelled.</white>");
		premium.setExpired("{prefix}<white>Premium migration request expired.</white>");
		premium.setNoPending("{prefix}<white>No pending premium migration.</white>");
		premium.setPendingExists("{prefix}<white>Premium migration already pending.</white>");
		premium.setAlreadyPrimary("{prefix}<white>Premium is already your primary provider.</white>");
		commands.setPremium(premium);
		messages.setCommands(commands);

		return messages;
	}
}
