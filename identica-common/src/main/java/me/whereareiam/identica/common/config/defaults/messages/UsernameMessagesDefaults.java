package me.whereareiam.identica.common.config.defaults.messages;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.model.config.UsernameMessages;

import java.util.List;

@Singleton
public class UsernameMessagesDefaults implements DefaultsProvider<UsernameMessages> {
	@Override
	public UsernameMessages supply(UsernameMessages messages) {
		UsernameMessages.Pipeline pipeline = new UsernameMessages.Pipeline();

		UsernameMessages.Pipeline.Prepare prepare = new UsernameMessages.Pipeline.Prepare();
		prepare.setFailed(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Unable to prepare authoritative username.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));

		UsernameMessages.Pipeline.Identity identity = new UsernameMessages.Pipeline.Identity();
		identity.setSynchronizationFailed(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Unable to synchronize username.</white>",
				"<white>Please contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));

		UsernameMessages.Pipeline.Policy policy = new UsernameMessages.Pipeline.Policy();
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
		policy.setTaken(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>The username <green>{username}</green> is already taken.</white>",
				"<white>Please join with another username.</white>",
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
