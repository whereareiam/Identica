package me.whereareiam.identica.common.config.defaults.messages;

import com.google.inject.Singleton;
import me.whereareiam.commandant.model.message.ExceptionMessages;
import me.whereareiam.commandant.model.message.HelpMessages;
import me.whereareiam.commandant.model.message.PaginationMessages;
import me.whereareiam.identica.model.config.Messages;

import java.util.List;

@Singleton
public class MessagesCommandDefaults {
	public Messages.Commands supply(Messages.Commands commands) {
		commands.setCurrentSessionRequired("{prefix}<white>You must have an <red>active session</red> to use this command.</white>");
		applyExceptions(commands);
		applyPagination(commands);
		applyHelp(commands);
		applyEnroll(commands);
		applyAvailability(commands);
		applyMigration(commands);
		applyReload(commands);
		applyAdmin(commands);

		return commands;
	}

	private void applyExceptions(Messages.Commands commands) {
		ExceptionMessages exceptionMessages = new ExceptionMessages();
		exceptionMessages.setNoPermission("{prefix}<white>You don't have \"<gray>{content}</gray>\" permission to use this command.</white>");
		exceptionMessages.setExecutionError("{prefix}<white>An error occurred while executing the command:</white> <gray>{content}</gray>");
		exceptionMessages.setInvalidSyntax("{prefix}<white>Invalid syntax, please use:</white> <gray>/{content}</gray>");
		exceptionMessages.setInvalidSyntaxBoolean("{prefix}<white>You tried to use <gray>{content}</gray> as a boolean, but it's not a valid value, please use <green>true</green> or <red>false</red>.</white>");
		exceptionMessages.setInvalidSyntaxNumber("{prefix}<white>You tried to use <gray>{content}</gray> as a number, but it's not a valid value, please use a valid number.</white>");
		exceptionMessages.setInvalidSyntaxString("{prefix}<white>You tried to use <gray>{content}</gray> as a string, but it's not a valid value, please use a valid string.</white>");
		exceptionMessages.setInvalidSender("{prefix}<white>You cannot execute this command from this context.</white>");
		commands.setExceptions(exceptionMessages);

	}

	private void applyPagination(Messages.Commands commands) {
		PaginationMessages paginationMessages = new PaginationMessages();
		paginationMessages.setShowPaginationIfOnePage(false);
		paginationMessages.setFormat("\n {previous}<white>Pagination</white> <gray>[{current}/{max}]</gray>{next} \n");
		paginationMessages.setShowPreviousEvenIfFirst(false);
		paginationMessages.setPreviousTagFormat("<red><click:run_command:/identica help {previousPage}>«</red> ");
		paginationMessages.setShowNextEvenIfLast(false);
		paginationMessages.setNextTagFormat(" <green><click:run_command:/identica help {nextPage}>»</green>");
		commands.setPagination(paginationMessages);
	}

	private void applyHelp(Messages.Commands commands) {
		HelpMessages helpMessages = new HelpMessages();
		helpMessages.setFormat(List.of(
				" ",
				" <green><bold>Identica</bold> <white>Command help",
				" ",
				"{commands}",
				"{pagination}"
		));
		helpMessages.setCommandFormat("  <yellow>/{command}{arguments}</yellow> <dark_gray>- <white>{description}");
		helpMessages.setNoCommands("  <red>No commands found</red>");
		helpMessages.setCommandsPerPage(7);

		HelpMessages.Format argumentFormat = new HelpMessages.Format();
		argumentFormat.setArgument("<gray>[{argument}]</gray>");
		argumentFormat.setOptionalArgument("<gray>({argument})</gray>");
		helpMessages.setArgumentFormat(argumentFormat);
		commands.setHelp(helpMessages);
	}

	private void applyEnroll(Messages.Commands commands) {
		Messages.Commands.Enroll enroll = new Messages.Commands.Enroll();
		enroll.setNoPending("{prefix}<white>No pending enrollment available.</white>");
		enroll.setCompleted("{prefix}<white>Enrollment completed.</white>");
		commands.setEnroll(enroll);
	}

	private void applyAvailability(Messages.Commands commands) {
		Messages.Commands.Availability availability = new Messages.Commands.Availability();

		Messages.Commands.Availability.Username username = new Messages.Commands.Availability.Username();
		username.setFree("{prefix}<white>Username <gray>{username}</gray> is available.</white>");
		username.setTaken("{prefix}<white>Username <gray>{username}</gray> is already taken.</white>");
		availability.setUsername(username);

		commands.setAvailability(availability);
	}

	private void applyMigration(Messages.Commands commands) {
		Messages.Commands.Migration migration = new Messages.Commands.Migration();
		Messages.Commands.Migration.Listing migrationList = new Messages.Commands.Migration.Listing();
		migrationList.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Provider links for <aqua>{target}</aqua>:</white>",
				"{entries}",
				" "
		));
		Messages.Commands.EntryFormat migrationEntry = new Messages.Commands.EntryFormat();
		migrationEntry.setFormat("  <dark_gray>▪</dark_gray> <white>{providerName}</white> <gray>({providerId})</gray> <green>{primary}</green>");
		migrationEntry.setEmptyFormat("  <dark_gray>▪</dark_gray> <white>{providerId}</white>");
		migrationList.setEntry(migrationEntry);
		migration.setList(migrationList);

		Messages.Commands.Migration.Links migrationLinks = new Messages.Commands.Migration.Links();
		migrationLinks.setNoLinks("{prefix}<white>No provider links found for <gray>{target}</gray>.</white>");
		migrationLinks.setNotLinked("{prefix}<white><gold>{provider}</gold> is not linked for <gray>{target}</gray>.</white>");
		migration.setLinks(migrationLinks);

		Messages.Commands.Migration.Start migrationStart = new Messages.Commands.Migration.Start();
		migrationStart.setPendingExists("{prefix}<white>Migration already pending for <gray>{target}</gray>.</white>");
		migrationStart.setStarted("{prefix}<white>Migration started for <gray>{target}</gray> to <gold>{provider}</gold>.</white>");
		migrationStart.setProviderUnsupported("{prefix}<white><gold>{provider}</gold> does not support migration.</white>");
		migrationStart.setProviderUnavailable("{prefix}<white><gold>{provider}</gold> is currently unavailable for migration.</white>");
		migration.setStart(migrationStart);

		Messages.Commands.Migration.Cancel migrationCancel = new Messages.Commands.Migration.Cancel();
		migrationCancel.setCancelled("{prefix}<white>Migration cancelled for <gray>{target}</gray>.</white>");
		migrationCancel.setNoPending("{prefix}<white>No pending migration for <gray>{target}</gray>.</white>");
		migration.setCancel(migrationCancel);

		Messages.Commands.Migration.Primary migrationPrimary = new Messages.Commands.Migration.Primary();
		migrationPrimary.setSet("{prefix}<white>Primary provider set to <gold>{provider}</gold> for <gray>{target}</gray>.</white>");
		migrationPrimary.setAlreadyPrimary("{prefix}<white><gold>{provider}</gold> is already primary for <gray>{target}</gray>.</white>");
		migration.setPrimary(migrationPrimary);

		Messages.Commands.Migration.Drop migrationDrop = new Messages.Commands.Migration.Drop();
		migrationDrop.setDropped("{prefix}<white>Dropped <gold>{provider}</gold> for <gray>{target}</gray>.</white>");
		migrationDrop.setPrimaryDenied("{prefix}<white>Cannot drop primary provider. Change primary first.</white>");
		migrationDrop.setLastLinkDenied("{prefix}<white>Cannot drop the only provider link.</white>");
		migration.setDrop(migrationDrop);

		migration.setTargetNotFound("{prefix}<white>No account found for <gray>{target}</gray>.</white>");
		migration.setLocked("{prefix}<white>Username is not available. Migration is locked.</white>");
		commands.setMigration(migration);
	}

	private Messages.Commands.Admin.Sessions sessions() {
		Messages.Commands.Admin.Sessions sessions = new Messages.Commands.Admin.Sessions();
		sessions.setUnknown("unknown");
		Messages.Commands.Admin.Sessions.Listing sessionList = new Messages.Commands.Admin.Sessions.Listing();
		sessionList.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Active sessions:</white>",
				"{entries}",
				" "
		));
		Messages.Commands.EntryFormat sessionListEntry = new Messages.Commands.EntryFormat();
		sessionListEntry.setFormat("   <dark_gray>▪</dark_gray> <gray><click:run_command:/identica admin session info {uniqueId}>[{uniqueId}]</click></gray>\n     <white>Provider: <green>{eligibility} <gray>| <white>Username: <green>{username} <gray>| <white>Proxy: <green>{server}");
		sessionListEntry.setEmptyFormat("  <dark_gray>▪</dark_gray> <gray><click:run_command:/identica admin session info {uniqueId}>[{uniqueId}]</click></gray>");
		sessionList.setEntry(sessionListEntry);
		sessionList.setEmpty("{prefix}<white>No active sessions.</white>");
		sessions.setListing(sessionList);

		Messages.Commands.Admin.Sessions.Detail sessionStatus = new Messages.Commands.Admin.Sessions.Detail();
		sessionStatus.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Session information for <aqua>{username}</aqua></white>",
				" ",
				"  <gray>Information:</gray>",
				"   <white>UUID: <gray>{uniqueId}</gray></white>",
				"   <white>Subject: <gray>{subject}</gray></white>",
				"   <white>Session: <gray>{session}</gray></white>",
				" ",
				"   <white>Provider: <gray>{eligibility}</gray></white>",
				"   <white>IP: <gray>{ip}</gray></white>",
				"   <white>Proxy: <gray>{server}</gray></white>",
				"   <white>Connection: <gray>{connection}</gray></white>",
				" ",
				"   <white>Created: <gray>{created}</gray></white>",
				" ",
				"  <gray>Username:</gray>",
				"   <white>Original: <gray>{original}</gray></white>",
				"   <white>Effective: <gray>{effective}</gray></white>",
				" "
		));
		sessionStatus.setNotFound("{prefix}<white>No active session found for <gray>{target}</gray>.</white>");
		sessions.setStatus(sessionStatus);

		Messages.Commands.Admin.Sessions.Multiple sessionsMultiple = new Messages.Commands.Admin.Sessions.Multiple();
		sessionsMultiple.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Found <gray>{count}</gray> sessions for</white>",
				"  <white>username <gray>{target}</gray></white>",
				" ",
				"  <white>Matches:</white>",
				"{entries}",
				" "
		));
		Messages.Commands.EntryFormat sessionsMultipleEntry = new Messages.Commands.EntryFormat();
		sessionsMultipleEntry.setFormat("  <click:run_command:/{command} {uniqueId}>▪ <gray>{uniqueId}</gray>: <green>{username}</green></click>");
		sessionsMultipleEntry.setEmptyFormat("  <click:run_command:/{command} {uniqueId}>▪ <gray>{uniqueId}</gray></click>");
		sessionsMultiple.setEntry(sessionsMultipleEntry);
		sessions.setMultiple(sessionsMultiple);

		Messages.Commands.Admin.Sessions.End sessionEnd = new Messages.Commands.Admin.Sessions.End();
		sessionEnd.setEnded("{prefix}<white>Ended session for <gray>{username}</gray> [<gray>{uniqueId}</gray>].</white>");
		sessionEnd.setNotFound("{prefix}<white>No active session found for <gray>{target}</gray>.</white>");
		sessionEnd.setDisconnect(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Your session was ended.</white>",
				"<white>Please rejoin to continue.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));
		sessions.setEnd(sessionEnd);

		return sessions;
	}

	private void applyReload(Messages.Commands commands) {
		Messages.Commands.Reload reload = new Messages.Commands.Reload();
		reload.setSuccess("{prefix}<white>Configuration reloaded <green>successfully</green>!");
		reload.setError("{prefix}<white>An <red>error occurred</red> while reloading: <gray>{error}</gray>");
		commands.setReload(reload);
	}

	private Messages.Commands.Admin.Clear clear() {
		Messages.Commands.Admin.Clear clear = new Messages.Commands.Admin.Clear();
		clear.setConfirm(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Clear request for <aqua>{target}</aqua></white>",
				"  <white>Scope: <aqua>{scope}</aqua></white>",
				"  <white>UUID: <gray>{uniqueId}</gray></white>",
				" ",
				"  <click:run_command:/identica admin clear confirm><green>[CONFIRM]</green></click>       " +
						"<click:run_command:/identica admin clear cancel><red>[CANCEL]</red></click>",
				" "
		));
		clear.setNoPending("{prefix}<white>No pending clear request.</white>");
		clear.setExpired("{prefix}<white>Clear request expired, please run the command again.</white>");
		clear.setCancelled("{prefix}<white>Clear request cancelled.</white>");
		clear.setNotFound("{prefix}<white>No account found for <gray>{target}</gray>.</white>");

		Messages.Commands.Admin.Clear.Multiple clearMultiple = new Messages.Commands.Admin.Clear.Multiple();
		clearMultiple.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Found <gray>{count}</gray> accounts for</white>",
				"  <white>username <gray>{target}</gray></white>",
				" ",
				"  <white>Matches:</white>",
				"{entries}",
				" "
		));

		Messages.Commands.EntryFormat clearEntryFormat = new Messages.Commands.EntryFormat();
		clearEntryFormat.setFormat("  <click:run_command:/{command} {uniqueId}>▪ <gray>{uniqueId}</gray>: <green>{username}</green></click>");
		clearEntryFormat.setEmptyFormat("  <click:run_command:/{command} {uniqueId}>▪ <gray>{uniqueId}</gray></click>");
		clearMultiple.setEntry(clearEntryFormat);

		clear.setMultiple(clearMultiple);
		clear.setSuccess("{prefix}<white>Cleared <gold>{scope}</gold> for <gray>{uniqueId}</gray>.</white>");
		clear.setError("{prefix}<white>An <red>error occurred</red> while clearing: <gray>{error}</gray></white>");
		clear.setDisconnect(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Your session has been cleared.</white>",
				"<white>Please rejoin to continue.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));

		return clear;
	}

	private void applyAdmin(Messages.Commands commands) {
		Messages.Commands.Admin admin = new Messages.Commands.Admin();
		admin.setClear(clear());
		admin.setSessions(sessions());
		Messages.Commands.Admin.Delete delete = new Messages.Commands.Admin.Delete();
		delete.setConfirm(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Delete request for <aqua>{target}</aqua></white>",
				"  <white>UUID: <gray>{uniqueId}</gray></white>",
				" ",
				"  <click:run_command:/identica admin delete confirm><green>[CONFIRM]</green></click>       " +
						"<click:run_command:/identica admin delete cancel><red>[CANCEL]</red></click>",
				" "
		));
		delete.setNoPending("{prefix}<white>No pending delete request.</white>");
		delete.setExpired("{prefix}<white>Delete request expired, please run the command again.</white>");
		delete.setCancelled("{prefix}<white>Delete request cancelled.</white>");
		delete.setNotFound("{prefix}<white>No account found for <gray>{target}</gray>.</white>");

		Messages.Commands.Admin.Clear.Multiple multiple = new Messages.Commands.Admin.Clear.Multiple();
		multiple.setBody(List.of(
				" ",
				" <green><bold>Identica</bold>",
				" ",
				"  <white>Found <gray>{count}</gray> accounts for</white>",
				"  <white>username <gray>{target}</gray></white>",
				" ",
				"  <white>Matches:</white>",
				"{entries}",
				" "
		));

		Messages.Commands.EntryFormat entryFormat = new Messages.Commands.EntryFormat();
		entryFormat.setFormat("  <click:run_command:/{command} {uniqueId}>▪ <gray>{uniqueId}</gray>: <green>{username}</green></click>");
		entryFormat.setEmptyFormat("  <click:run_command:/{command} {uniqueId}>▪ <gray>{uniqueId}</gray></click>");
		multiple.setEntry(entryFormat);
		delete.setMultiple(multiple);

		delete.setSuccess("{prefix}<white>Deleted account <gray>{uniqueId}</gray>.</white>");
		delete.setError("{prefix}<white>An <red>error occurred</red> while deleting: <gray>{error}</gray></white>");
		delete.setDisconnect(List.of(
				"<green>ɪᴅᴇɴᴛɪᴄᴀ",
				"",
				"<white>Your account has been deleted.</white>",
				"<white>Please rejoin to continue.</white>",
				"",
				"<dark_gray>discord.arcadeya.com"
		));

		Messages.Commands.Admin.Reservation reservation = new Messages.Commands.Admin.Reservation();
		reservation.setSet("{prefix}<white>Reserved <gray>{key}</gray> for <gray>{uniqueId}</gray>.</white>");
		reservation.setInfo("{prefix}<white>Reservation <gray>{key}</gray> points to <gray>{uniqueId}</gray>.</white>");
		reservation.setDeleted("{prefix}<white>Deleted reservation <gray>{key}</gray>.</white>");
		reservation.setNotFound("{prefix}<white>No reservation found for <gray>{key}</gray>.</white>");
		reservation.setInvalidKey("{prefix}<white>Invalid reservation key <gray>{key}</gray>.</white>");

		admin.setDelete(delete);
		admin.setReservation(reservation);
		commands.setAdmin(admin);
	}

}
