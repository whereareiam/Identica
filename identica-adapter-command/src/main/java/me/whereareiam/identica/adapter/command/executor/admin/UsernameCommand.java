package me.whereareiam.identica.adapter.command.executor.admin;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.type.DateTimePattern;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.identity.username.AccountUsernameDuplicate;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.persistence.username.AccountUsernameDuplicatePersistenceService;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.template.message.TemplateSection;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Lists the usernames that several accounts share without regard to case, so that an administrator can leave each
 * of them to one account.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UsernameCommand {
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());
	private static final String NONE = "-";

	private final Provider<Messages> messagesProvider;
	private final AccountUsernameDuplicatePersistenceService duplicatePersistenceService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;

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
