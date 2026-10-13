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
				"<white>Unable to prepare your username.</white>",
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
		policy.setTaken(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>The username <green>{username}</green> is already taken.</white>",
				"<white>Please join with another username.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		policy.setHolderReplaced(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>The owner of the username <green>{username}</green> has joined.</white>",
				"<white>Join again to play as <green>{renamed}</green>.</white>",
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
