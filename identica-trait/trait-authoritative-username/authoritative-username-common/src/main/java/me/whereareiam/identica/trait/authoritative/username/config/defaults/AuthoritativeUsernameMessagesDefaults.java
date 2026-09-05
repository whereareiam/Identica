package me.whereareiam.identica.trait.authoritative.username.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.trait.authoritative.username.model.AuthoritativeUsernameMessages;

import java.util.List;

@Singleton
public class AuthoritativeUsernameMessagesDefaults implements DefaultsProvider<AuthoritativeUsernameMessages> {
	@Override
	public AuthoritativeUsernameMessages supply(AuthoritativeUsernameMessages messages) {
		AuthoritativeUsernameMessages.Pipeline pipeline = new AuthoritativeUsernameMessages.Pipeline();

		AuthoritativeUsernameMessages.Pipeline.Prepare prepare = new AuthoritativeUsernameMessages.Pipeline.Prepare();
		prepare.setFailed(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Unable to prepare authoritative username.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));

		AuthoritativeUsernameMessages.Pipeline.Identity identity = new AuthoritativeUsernameMessages.Pipeline.Identity();
		identity.setSynchronizationFailed(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Unable to synchronize username.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));

		AuthoritativeUsernameMessages.Pipeline.Policy policy = new AuthoritativeUsernameMessages.Pipeline.Policy();
		policy.setPersistenceFailed(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Unable to persist username changes.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		policy.setConflictDenied(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>That username is currently unavailable.</white>",
				"<white>Please contact a server administrator if this looks wrong.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		policy.setEntrypointRequired(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>This username is already active through another provider.</white>",
				"<white>Use the correct entrypoint for <green>{existingProvider}</green> or <green>{incomingProvider}</green>.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));

		pipeline.setPrepare(prepare);
		pipeline.setIdentity(identity);
		pipeline.setPolicy(policy);
		messages.setPipeline(pipeline);

		return messages;
	}
}
