package me.whereareiam.identica.feature.restriction.join.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionMessages;

import java.util.List;

@Singleton
public class JoinRestrictionMessagesDefaults implements DefaultsProvider<JoinRestrictionMessages> {
	@Override
	public JoinRestrictionMessages supply(JoinRestrictionMessages messages) {
		messages.setDenied(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>{providerName} is temporarily unavailable.</white>",
				"<white>Allowed joins currently require: <green>{allow}</green>.</white>",
				"<white>If you think this is a mistake, contact a server administrator.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));

		JoinRestrictionMessages.Commands commands = new JoinRestrictionMessages.Commands();
		commands.setEnabled("{prefix}<white>Enabled provider restriction for <gold>{providerName}</gold> <gray>({providerId})</gray> with <green>{allow}</green> allowed.</white>");
		commands.setDisabled("{prefix}<white>Disabled provider restriction for <gold>{providerName}</gold> <gray>({providerId})</gray>.</white>");
		commands.setEnableFailed("{prefix}<white>Could not enable provider restriction for <gray>{provider}</gray>.</white>");
		commands.setProviderNotFound("{prefix}<white>Unknown provider <gray>{provider}</gray>.</white>");

		JoinRestrictionMessages.Commands.Status status = new JoinRestrictionMessages.Commands.Status();
		status.setNotFound("{prefix}<white>Unknown provider <gray>{provider}</gray>.</white>");
		status.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Provider restriction for <gold>{providerName}</gold> <gray>({providerId})</gray></white>",
				" ",
				"  <white>Runtime active:</white> <gray>{active}</gray>",
				"  <white>Allow:</white> <gray>{allow}</gray>",
				" "
		));

		JoinRestrictionMessages.Commands.Status.Labels labels = new JoinRestrictionMessages.Commands.Status.Labels();
		labels.setEnabled("<green>Enabled</green>");
		labels.setDisabled("<red>Disabled</red>");
		status.setLabels(labels);

		JoinRestrictionMessages.Commands.Status.Listing listing = new JoinRestrictionMessages.Commands.Status.Listing();
		listing.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Provider restriction status:</white>",
				"{entries}",
				" "
		));
		listing.setEmpty("{prefix}<white>No providers available.</white>");

		JoinRestrictionMessages.Commands.Status.Listing.Entries entries =
				new JoinRestrictionMessages.Commands.Status.Listing.Entries();
		entries.setPopulated("   <dark_gray>▪</dark_gray> <white>{providerName}:</white> {status}\n     <gray>[{allow}]</gray>");
		entries.setEmpty("   <dark_gray>▪</dark_gray> <white>{providerName}:</white> {status}");
		listing.setEntries(entries);
		status.setList(listing);
		commands.setStatus(status);
		messages.setCommands(commands);
		return messages;
	}
}
