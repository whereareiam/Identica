package me.whereareiam.identica.adapter.command.executor;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.adapter.command.suggestion.CrossPlayerSuggestions;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.annotation.Suggestions;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.model.identity.provider.AccountProviderLink;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.migration.operation.MigrationCancel;
import me.whereareiam.identica.model.migration.operation.MigrationResult;
import me.whereareiam.identica.model.migration.operation.MigrationStart;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.persistence.AccountPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.persistence.provider.ProviderProfilePersistenceService;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.service.MigrationService;
import me.whereareiam.identica.type.migration.MigrationCancelScope;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import me.whereareiam.identica.type.migration.MigrationResultStatus;
import me.whereareiam.identica.util.UniqueIdUtil;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.template.message.TemplateSection;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MigrationCommand {
	private final Provider<Messages> messagesProvider;
	private final AccountPersistenceService accountPersistenceService;
	private final ProviderLinkPersistenceService providerLinkPersistenceService;
	private final ProviderProfilePersistenceService providerProfilePersistenceService;
	private final ProviderManager providerManager;
	private final MigrationService migrationService;
	private final IdentityService identityService;

	@Definition("migration-list")
	@Command("identica migration list <target>")
	public void list(
			@NotNull Actor sender,
			@Argument("target") @Suggestions(CrossPlayerSuggestions.KEY) String target
	) {
		Messages.Commands.Migration messages = messagesProvider.get().getCommands().getMigration();
		Messages.Commands.Migration.Listing listMessages = messages.getList();
		ResolvedTarget resolved = resolveTarget(sender, target, messages);
		if (resolved == null)
			return;

		List<AccountProviderLink> links = providerLinkPersistenceService.findByUniqueId(resolved.uniqueId());
		if (links.isEmpty()) {
			sendMessage(sender, messages.getLinks().getNoLinks(), Map.of("target", resolved.display()));
			return;
		}

		Map<String, String> providerNames = resolveProviderNames();
		List<String> entries = buildEntries(listMessages.getEntry(), links, link -> {
			String providerId = link.getProviderId();
			String providerName = providerNames.get(providerId);

			Map<String, String> placeholders = new HashMap<>();
			placeholders.put("providerId", providerId);
			placeholders.put("providerName", providerName);
			placeholders.put("primary", link.isPrimaryLink() ? "<green>primary</green>" : "");
			return new EntryData(placeholders, providerName != null && !providerName.isBlank());
		});
		sender.sendMessage(Serializer.serialize(
				sender,
				Serializer.template(bodyTemplate(listMessages.getBody()))
						.placeholders(Map.of("target", resolved.display()))
						.section("entries", section -> section
								.lines(entries)
								.onMissing(TemplateSection.MissingSectionPolicy.APPEND))
						.render()
		));
	}

	@Definition("migration-start")
	@Command("identica migration start <target> <provider>")
	public void start(
			@NotNull Actor sender,
			@Argument("target") @Suggestions(CrossPlayerSuggestions.KEY) String target,
			@Argument("provider") String providerId
	) {
		Messages.Commands.Migration messages = messagesProvider.get().getCommands().getMigration();
		ResolvedTarget resolved = resolveTarget(sender, target, messages);
		if (resolved == null) return;

		MigrationResult result = migrationService.start(MigrationStart.builder()
				.connectionUniqueId(resolved.uniqueId())
				.uniqueId(resolved.uniqueId())
				.targetProviderId(providerId)
				.username(resolved.username())
				.initiator(MigrationInitiator.ADMIN)
				.initiatorUniqueId(sender.getUniqueId())
				.build());

		if (result.getStatus() == MigrationResultStatus.PENDING_EXISTS) {
			sendMessage(sender, messages.getStart().getPendingExists(), Map.of("target", resolved.display()));
			return;
		}

		if (result.getStatus() == MigrationResultStatus.ALREADY_PRIMARY) {
			sendMessage(sender, messages.getPrimary().getAlreadyPrimary(), Map.of(
					"target", resolved.display(),
					"provider", providerId
			));
			return;
		}

		if (result.getStatus() == MigrationResultStatus.PRIMARY_SET) {
			sendMessage(sender, messages.getPrimary().getSet(), Map.of(
					"target", resolved.display(),
					"provider", providerId
			));
			return;
		}

		if (result.getStatus() == MigrationResultStatus.PRECHECK_DENIED) {
			PlayerMessage message = result.getMessage();
			if (message != null && message.hasText()) {
				sender.sendMessage(Serializer.serialize(sender, message));
				return;
			}
		}
		if (result.getStatus() == MigrationResultStatus.TARGET_UNSUPPORTED) {
			sendMessage(sender, messages.getStart().getProviderUnsupported(), Map.of("provider", providerId));
			return;
		}
		if (result.getStatus() == MigrationResultStatus.PROVIDER_UNAVAILABLE) {
			sendMessage(sender, messages.getStart().getProviderUnavailable(), Map.of("provider", providerId));
			return;
		}

		if (result.getStatus() == MigrationResultStatus.STARTED) {
			sendMessage(sender, messages.getStart().getStarted(), Map.of(
					"target", resolved.display(),
					"provider", providerId
			));
		}
	}

	@Definition("migration-cancel")
	@Command("identica migration cancel <target>")
	public void cancel(
			@NotNull Actor sender,
			@Argument("target") @Suggestions(CrossPlayerSuggestions.KEY) String target
	) {
		Messages.Commands.Migration messages = messagesProvider.get().getCommands().getMigration();
		ResolvedTarget resolved = resolveTarget(sender, target, messages);
		if (resolved == null)
			return;

		MigrationResult result = migrationService.cancel(MigrationCancel.builder()
				.connectionUniqueId(resolved.uniqueId())
				.scope(MigrationCancelScope.ALL)
				.build());

		if (result.getStatus() == MigrationResultStatus.CANCELLED) {
			sendMessage(sender, messages.getCancel().getCancelled(), Map.of("target", resolved.display()));
			return;
		}

		sendMessage(sender, messages.getCancel().getNoPending(), Map.of("target", resolved.display()));
	}

	@Definition("migration-primary")
	@Command("identica migration primary <target> <provider>")
	public void primary(
			@NotNull Actor sender,
			@Argument("target") @Suggestions(CrossPlayerSuggestions.KEY) String target,
			@Argument("provider") String providerId
	) {
		Messages.Commands.Migration messages = messagesProvider.get().getCommands().getMigration();
		ResolvedTarget resolved = resolveTarget(sender, target, messages);
		if (resolved == null)
			return;

		List<AccountProviderLink> links = providerLinkPersistenceService.findByUniqueId(resolved.uniqueId());
		if (links.isEmpty()) {
			sendMessage(sender, messages.getLinks().getNoLinks(), Map.of("target", resolved.display()));
			return;
		}

		AccountProviderLink link = findLink(links, providerId);
		if (link == null) {
			sendMessage(sender, messages.getLinks().getNotLinked(), Map.of(
					"target", resolved.display(),
					"provider", providerId
			));
			return;
		}

		if (link.isPrimaryLink()) {
			sendMessage(sender, messages.getPrimary().getAlreadyPrimary(), Map.of(
					"target", resolved.display(),
					"provider", providerId
			));
			return;
		}

		providerLinkPersistenceService.setPrimaryExclusive(resolved.uniqueId(), providerId);
		sendMessage(sender, messages.getPrimary().getSet(), Map.of(
				"target", resolved.display(),
				"provider", providerId
		));
	}

	@Definition("migration-drop")
	@Command("identica migration drop <target> <provider>")
	public void drop(
			@NotNull Actor sender,
			@Argument("target") @Suggestions(CrossPlayerSuggestions.KEY) String target,
			@Argument("provider") String providerId
	) {
		Messages.Commands.Migration messages = messagesProvider.get().getCommands().getMigration();
		ResolvedTarget resolved = resolveTarget(sender, target, messages);
		if (resolved == null)
			return;

		List<AccountProviderLink> links = providerLinkPersistenceService.findByUniqueId(resolved.uniqueId());
		if (links.isEmpty()) {
			sendMessage(sender, messages.getLinks().getNoLinks(), Map.of("target", resolved.display()));
			return;
		}

		AccountProviderLink link = findLink(links, providerId);
		if (link == null) {
			sendMessage(sender, messages.getLinks().getNotLinked(), Map.of(
					"target", resolved.display(),
					"provider", providerId
			));
			return;
		}

		if (link.isPrimaryLink()) {
			sendMessage(sender, messages.getDrop().getPrimaryDenied(), Map.of());
			return;
		}

		if (links.size() <= 1) {
			sendMessage(sender, messages.getDrop().getLastLinkDenied(), Map.of());
			return;
		}

		providerLinkPersistenceService.delete(resolved.uniqueId(), providerId);
		providerProfilePersistenceService.delete(providerId, link.getProviderSubject());
		sendMessage(sender, messages.getDrop().getDropped(), Map.of(
				"target", resolved.display(),
				"provider", providerId
		));
	}

	private ResolvedTarget resolveTarget(
			@NotNull Actor sender,
			@NotNull String target,
			@NotNull Messages.Commands.Migration messages
	) {
		UUID parsed = UniqueIdUtil.parseUniqueId(target);
		if (parsed != null) {
			Optional<Account> account = accountPersistenceService.findByUniqueId(parsed);
			if (account.isEmpty()) {
				sendMessage(sender, messages.getTargetNotFound(), Map.of("target", target));
				return null;
			}
			return new ResolvedTarget(parsed, account.get().getUsername());
		}

		Optional<Identity> identity = identityService.find(target);
		if (identity.isPresent()) {
			Identity resolved = identity.get();
			return new ResolvedTarget(resolved.getUniqueId(), resolved.getUsername());
		}

		List<Account> matches = accountPersistenceService.findByUsername(target);
		if (matches.isEmpty()) {
			sendMessage(sender, messages.getTargetNotFound(), Map.of("target", target));
			return null;
		}

		Account account = matches.getFirst();
		return new ResolvedTarget(account.getUniqueId(), account.getUsername());
	}

	private Map<String, String> resolveProviderNames() {
		Map<String, String> names = new HashMap<>();
		for (InternalProvider provider : providerManager.getProviders()) {
			if (provider == null || provider.getDescriptor() == null)
				continue;
			names.putIfAbsent(provider.getDescriptor().getId(), provider.getDescriptor().getName());
		}
		return names;
	}

	private AccountProviderLink findLink(@NotNull List<AccountProviderLink> links, @NotNull String providerId) {
		for (AccountProviderLink link : links) {
			if (link == null)
				continue;
			String id = link.getProviderId();
			if (id.equalsIgnoreCase(providerId))
				return link;
		}
		return null;
	}

	private <T> List<String> buildEntries(
			Messages.Commands.EntryFormat entryFormat,
			List<T> entriesSource,
			Function<T, EntryData> entryResolver
	) {
		List<String> entries = new ArrayList<>();
		if (entryFormat == null || entryFormat.getFormat().isBlank())
			return entries;

		for (T entrySource : entriesSource) {
			EntryData data = entryResolver.apply(entrySource);

			if (data == null || data.placeholders() == null) continue;
			String template = data.complete()
					? entryFormat.getFormat()
					: entryFormat.getEmptyFormat();

			if (template.isBlank()) template = entryFormat.getFormat();
			if (template.isBlank()) continue;

			String entry = Serializer.render(template, data.placeholders());
			if (!entry.isBlank())
				entries.add(entry);
		}
		return entries;
	}

	private void sendMessage(@NotNull Actor sender, String message, Map<String, String> placeholders) {
		if (message == null || message.isBlank()) return;
		SerializerContent content = SerializerContent.builder()
				.receiver(sender)
				.message(message)
				.placeholders(placeholders)
				.build();

		sender.sendMessage(Serializer.serialize(content));
	}

	private record ResolvedTarget(UUID uniqueId, String username) {
		String display() {
			return username != null && !username.isBlank() ? username : uniqueId.toString();
		}
	}

	private @NotNull String bodyTemplate(List<String> lines) {
		if (lines == null || lines.isEmpty()) return "";

		return String.join("\n", lines.stream()
				.filter(Objects::nonNull)
				.toList());
	}

	private record EntryData(Map<String, String> placeholders, boolean complete) {
	}
}
