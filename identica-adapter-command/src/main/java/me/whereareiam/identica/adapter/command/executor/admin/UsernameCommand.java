package me.whereareiam.identica.adapter.command.executor.admin;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.adapter.command.executor.admin.base.AdminCommand;
import me.whereareiam.identica.adapter.command.suggestion.CrossPlayerSuggestions;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.annotation.Suggestions;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.identity.UsernameChangedEvent;
import me.whereareiam.identica.identity.account.AccountService;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.type.DateTimePattern;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.username.AccountUsernameDuplicate;
import me.whereareiam.identica.model.identity.username.AccountUsernameHistoryEntry;
import me.whereareiam.identica.model.identity.username.UsernameWriteResult;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.persistence.username.AccountUsernameDuplicatePersistenceService;
import me.whereareiam.identica.persistence.username.AccountUsernamePersistenceService;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.template.message.TemplateSection;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Manages usernames by hand: lists the usernames that several accounts share without regard to case, so that an
 * administrator can leave each of them to one account, and renames an account. A name set by hand is never changed
 * by Identica afterwards.
 */
@Singleton
public class UsernameCommand extends AdminCommand {
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());
	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final String NONE = "-";

	private final Provider<Messages> messagesProvider;
	private final AccountUsernameDuplicatePersistenceService duplicatePersistenceService;
	private final AccountUsernamePersistenceService usernamePersistenceService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final EventManager eventManager;

	@Inject
	public UsernameCommand(
			@NotNull AccountService accountService,
			@NotNull Provider<Messages> messagesProvider,
			@NotNull AccountUsernameDuplicatePersistenceService duplicatePersistenceService,
			@NotNull AccountUsernamePersistenceService usernamePersistenceService,
			@NotNull ProviderLinkPersistenceService providerLinkPersistenceService,
			@NotNull EventManager eventManager
	) {
		super(accountService);
		this.messagesProvider = messagesProvider;
		this.duplicatePersistenceService = duplicatePersistenceService;
		this.usernamePersistenceService = usernamePersistenceService;
		this.providerLinkPersistenceService = providerLinkPersistenceService;
		this.eventManager = eventManager;
	}

	@Definition("admin-username-rename")
	@Command("identica admin username rename <target> <username>")
	public void rename(
			@NotNull Actor sender,
			@Argument("target") @Suggestions(CrossPlayerSuggestions.KEY) String target,
			@Argument("username") String username
	) {
		Messages.Commands.Admin.Username.Rename messages = messagesProvider.get().getCommands().getAdmin().getUsername().getRename();
		if (!USERNAME.matcher(username).matches()) {
			sendMessage(sender, messages.getInvalid(), Map.of("username", username));
			return;
		}

		Account account = resolveTarget(sender, target, messages.getNotFound(), messages.getMultiple(), "identica admin username rename");
		if (account == null) return;

		AccountUsernameHistoryEntry change = AccountUsernameHistoryEntry.builder()
				.uniqueId(account.getUniqueId())
				.oldUsername(account.getUsername())
				.newUsername(username)
				.source(AccountUsernameSource.MANUAL)
				.changedAt(System.currentTimeMillis())
				.build();
		UsernameWriteResult result = usernamePersistenceService.rename(change);
		if (!result.isWritten()) {
			sendMessage(sender, messages.getTaken(), Map.of(
					"username", username,
					"holder", String.valueOf(result.getHolderUniqueId())
			));
			return;
		}

		eventManager.call(new UsernameChangedEvent(account.getUniqueId(), account.getUsername(), username, null));
		sendMessage(sender, messages.getSuccess(), Map.of(
				"previous", account.getUsername(),
				"username", username,
				"uniqueId", account.getUniqueId().toString()
		));
	}

	@Definition("admin-username-duplicates")
	@Command("identica admin username duplicates")
	public void duplicates(@NotNull Actor sender) {
		Messages.Commands.Admin.Username.Duplicates messages = messagesProvider.get().getCommands().getAdmin().getUsername().getDuplicates();
		List<AccountUsernameDuplicate> duplicates = duplicatePersistenceService.findAll();
		if (duplicates.isEmpty()) {
			sender.sendMessage(Serializer.serialize(sender, messages.getEmpty(), Map.of()));
			return;
		}

		DateTimePattern pattern = messagesProvider.get().getFormat().getTemporal().getDateTime();
		DateTimeFormatter formatter = pattern != null ? pattern.formatter(TIME_FORMATTER) : TIME_FORMATTER;
		List<String> entries = new ArrayList<>();
		for (AccountUsernameDuplicate duplicate : duplicates) {
			entries.add(Serializer.render(messages.getGroup(), Map.of(
					"username", duplicate.getUsername(),
					"count", String.valueOf(duplicate.getAccounts().size())
			)));
			for (Account account : duplicate.getAccounts())
				entries.add(Serializer.render(messages.getEntry(), Map.of(
						"username", account.getUsername(),
						"uniqueId", account.getUniqueId().toString(),
						"providers", providers(account),
						"lastSeen", account.getLastSeenAt() > 0 ? formatter.format(Instant.ofEpochMilli(account.getLastSeenAt())) : NONE
				)));
		}

		String body = Serializer.template(String.join("\n", messages.getBody()))
				.section("entries", section -> section
						.lines(entries)
						.onMissing(TemplateSection.MissingSectionPolicy.APPEND))
				.render();
		sender.sendMessage(Serializer.serialize(sender, body, Map.of("count", String.valueOf(duplicates.size()))));
	}

	private @NotNull String providers(@NotNull Account account) {
		String providers = providerLinkPersistenceService.findByUniqueId(account.getUniqueId()).stream()
				.map(AccountProviderLink::getProviderId)
				.collect(Collectors.joining(", "));

		return providers.isEmpty() ? NONE : providers;
	}
}
