package me.whereareiam.identica.feature.restriction.join.command;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.annotation.Suggestions;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.feature.restriction.RestrictionService;
import me.whereareiam.identica.feature.restriction.join.JoinRestrictionType;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionMessages;
import me.whereareiam.identica.feature.restriction.model.RestrictionStatus;
import me.whereareiam.identica.feature.restriction.type.RestrictionSignal;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.template.message.TemplateSection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class JoinRestrictionCommand {
	private final Provider<JoinRestrictionMessages> messagesProvider;
	private final RestrictionService restrictionService;
	private final ProviderOperations providerOperations;

	@Definition("admin-provider-restriction-join-enable")
	@Command("identica admin provider restriction join enable <provider>")
	public void enable(
			@NotNull Actor sender,
			@Argument("provider") @Suggestions("providerId") String providerId
	) {
		JoinRestrictionMessages.Commands messages = messages();
		Optional<RestrictionStatus> current = restrictionService.status(JoinRestrictionType.TYPE, providerId);
		if (current.isEmpty()) {
			sender.sendMessage(Serializer.serialize(sender, messages.getProviderNotFound(), Map.of("provider", providerId)));
			return;
		}

		RestrictionStatus status = restrictionService.enable(JoinRestrictionType.TYPE, providerId);
		if (!status.isActive()) {
			sender.sendMessage(Serializer.serialize(sender, messages.getEnableFailed(), Map.of("provider", status.getProviderId())));
			return;
		}

		sender.sendMessage(Serializer.serialize(sender, messages.getEnabled(), placeholders(status)));
	}

	@Definition("admin-provider-restriction-join-disable")
	@Command("identica admin provider restriction join disable <provider>")
	public void disable(
			@NotNull Actor sender,
			@Argument("provider") @Suggestions("providerId") String providerId
	) {
		JoinRestrictionMessages.Commands messages = messages();
		Optional<RestrictionStatus> current = restrictionService.status(JoinRestrictionType.TYPE, providerId);
		if (current.isEmpty()) {
			sender.sendMessage(Serializer.serialize(sender, messages.getProviderNotFound(), Map.of("provider", providerId)));
			return;
		}

		RestrictionStatus status = restrictionService.disable(JoinRestrictionType.TYPE, providerId);
		sender.sendMessage(Serializer.serialize(sender, messages.getDisabled(), placeholders(status)));
	}

	@Definition("admin-provider-restriction-join-status")
	@Command("identica admin provider restriction join status [provider]")
	public void status(
			@NotNull Actor sender,
			@Argument("provider") @Suggestions("providerId") @Nullable String providerId
	) {
		if (providerId == null || providerId.isBlank()) {
			list(sender);
			return;
		}

		JoinRestrictionMessages.Commands messages = messages();
		RestrictionStatus status = restrictionService.status(JoinRestrictionType.TYPE, providerId).orElse(null);
		if (status == null) {
			sender.sendMessage(Serializer.serialize(sender, messages.getStatus().getNotFound(), Map.of("provider", providerId)));
			return;
		}

		sender.sendMessage(Serializer.serialize(
				sender,
				Serializer.render(bodyTemplate(messages.getStatus().getBody()), placeholders(status))
		));
	}

	private void list(@NotNull Actor sender) {
		JoinRestrictionMessages.Commands.Status.Listing listing = messages().getStatus().getList();
		List<RestrictionStatus> statuses = restrictionService.statuses(JoinRestrictionType.TYPE);
		if (statuses.isEmpty()) {
			sender.sendMessage(Serializer.serialize(sender, listing.getEmpty(), Map.of()));
			return;
		}

		List<String> entries = new ArrayList<>();
		for (RestrictionStatus status : statuses) {
			String template = !status.getAllow().isEmpty()
					? listing.getEntries().getPopulated()
					: listing.getEntries().getEmpty();
			String entry = Serializer.render(template, placeholders(status));
			if (!entry.isBlank())
				entries.add(entry);
		}
		if (entries.isEmpty()) {
			sender.sendMessage(Serializer.serialize(sender, listing.getEmpty(), Map.of()));
			return;
		}

		sender.sendMessage(Serializer.serialize(
				sender,
				Serializer.template(bodyTemplate(listing.getBody()))
						.section("entries", section -> section
								.lines(entries)
								.onMissing(TemplateSection.MissingSectionPolicy.APPEND))
						.render()
		));
	}

	private JoinRestrictionMessages.Commands messages() {
		return messagesProvider.get().getCommands();
	}

	private Map<String, String> placeholders(@NotNull RestrictionStatus status) {
		String providerName = providerOperations.displayProviderName(status.getProviderId());
		Map<String, String> placeholders = new HashMap<>();
		placeholders.put("provider", status.getProviderId());
		placeholders.put("providerId", status.getProviderId());
		placeholders.put("providerName", providerName != null ? providerName : status.getProviderId());
		placeholders.put("status", statusLabel(status.isActive()));
		placeholders.put("active", String.valueOf(status.isActive()));
		placeholders.put("allow", describeAllow(status.getAllow()));
		return placeholders;
	}

	private @NotNull String statusLabel(boolean active) {
		JoinRestrictionMessages.Commands.Status.Labels labels = messages().getStatus().getLabels();
		return active
				? labels.getEnabled()
				: labels.getDisabled();
	}

	private @NotNull String describeAllow(@NotNull Set<RestrictionSignal> allow) {
		if (allow.isEmpty()) return "NONE";
		return allow.stream()
				.map(RestrictionSignal::getId)
				.collect(Collectors.joining(", "));
	}

	private @NotNull String bodyTemplate(@NotNull List<String> lines) {
		return String.join("\n", lines.stream()
				.filter(Objects::nonNull)
				.toList());
	}
}
