package me.whereareiam.identica.adapter.command.executor.admin;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.commandant.Pagination;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.*;
import me.whereareiam.identica.database.AccountPersistenceService;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.SessionCloseRequest;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.config.type.DateTimePattern;
import me.whereareiam.identica.model.identity.Account;
import me.whereareiam.identica.util.UniqueIdUtil;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.template.message.TemplateSection;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class SessionsCommand {
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneId.systemDefault());
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneId.systemDefault());

	private final Provider<Messages> messagesProvider;
	private final Provider<Commands> commandsProvider;
	private final AccountPersistenceService accountPersistenceService;
	private final SessionService sessionService;
	private final IdentityService identityService;

	@Definition("admin-session")
	@Command("identica admin session [page]")
	public void sessions(
			@NotNull Actor sender,
			@Argument("page") @Default("1") @Range(min = "1") int page
	) {
		list(sender, page);
	}

	@Definition("admin-session-list")
	@Command("identica admin session list [page]")
	public void list(
			@NotNull Actor sender,
			@Argument("page") @Default("1") @Range(min = "1") int page
	) {
		Messages.Commands.Admin.Sessions messages = messagesProvider.get().getCommands().getAdmin().getSessions();
		Messages.Commands.Admin.Sessions.Listing listMessages = messages.getListing();
		int pageSize = commandsProvider.get().getBehavior().getSessions().getListPageSize();
		SerializerOptions.PlaceholderFormat format = Serializer.getEngine().getPlaceholderFormat();

		SessionService.Page pageData = sessionService.list(page, pageSize).join();
		int total = pageData.total();
		if (total <= 0) {
			sender.sendMessage(Serializer.serialize(sender, listMessages.getEmpty(), Map.of()));
			return;
		}

		int maxPage = (int) Math.ceil(total / (double) pageSize);
		if (page > maxPage) {
			page = maxPage;
			pageData = sessionService.list(page, pageSize).join();
		}

		List<Session> sessions = resolveSessions(pageData.entries());
		if (sessions.isEmpty()) {
			sender.sendMessage(Serializer.serialize(sender, listMessages.getEmpty(), Map.of()));
			return;
		}

		List<String> entries = buildEntries(listMessages.getEntry(), sessions, session -> {
			String username = session.getEffectiveUsername();
			if (!isPresent(username)) username = session.getOriginalUsername();

			String provider = session.getProviderId();

			Map<String, String> placeholders = new HashMap<>();
			placeholders.put("username", username);
			placeholders.put("uniqueId", session.getUniqueId().toString());
			placeholders.put("eligibility", provider);
			placeholders.put("session", session.getSessionId());
			placeholders.put("ip", session.getIp());
			return new EntryData(placeholders, isPresent(username) && isPresent(provider));
		});
		sender.sendMessage(Serializer.serialize(
				sender,
				Pagination.builder(messagesProvider.get().getCommands().getPagination())
						.placeholderFormat(format)
						.build()
						.build(
								Serializer.template(bodyTemplate(listMessages.getBody()))
										.section("entries", section -> section
												.lines(entries)
												.onMissing(TemplateSection.MissingSectionPolicy.APPEND))
										.render(),
								page,
								pageSize,
								total
						)
		));
	}

	@Definition("admin-session-info")
	@Command("identica admin session info <target>")
	public void info(@NotNull Actor sender, @Argument("target") String target) {
		Messages.Commands.Admin.Sessions messages = messagesProvider.get().getCommands().getAdmin().getSessions();
		Messages.Commands.Admin.Sessions.Detail statusMessages = messages.getStatus();
		String unknown = messages.getUnknown();

		ResolvedTarget resolved = resolveTarget(sender, target, messages, "identica admin session info", statusMessages.getNotFound());
		if (resolved == null) return;

		Optional<Session> session = sessionService.findByUniqueId(resolved.uniqueId()).join();
		if (session.isEmpty()) {
			sender.sendMessage(Serializer.serialize(sender, statusMessages.getNotFound(), Map.of("target", target)));
			return;
		}

		Session resolvedSession = session.get();
		SessionConnection connection = resolvedSession.getConnection();
		Messages.Format.Temporal temporal = messagesProvider.get().getFormat().getTemporal();
		DateTimeFormatter dateFormatter = resolveFormatter(temporal.getDate(), DATE_FORMATTER);
		DateTimeFormatter dateTimeFormatter = resolveFormatter(temporal.getDateTime(), TIME_FORMATTER);
		String createdDate = resolvedSession.getCreatedAt() > 0
				? dateFormatter.format(Instant.ofEpochMilli(resolvedSession.getCreatedAt()))
				: unknown;
		String createdDateTime = resolvedSession.getCreatedAt() > 0
				? dateTimeFormatter.format(Instant.ofEpochMilli(resolvedSession.getCreatedAt()))
				: unknown;

		sender.sendMessage(Serializer.serialize(
				sender,
				Serializer.render(
						String.join("\n", statusMessages.getBody().stream()
								.filter(Objects::nonNull)
								.toList()),
						Map.ofEntries(
								Map.entry("username", resolveUsername(resolvedSession, unknown)),
								Map.entry("original", safe(resolvedSession.getOriginalUsername(), unknown)),
								Map.entry("effective", safe(resolvedSession.getEffectiveUsername(), unknown)),
								Map.entry("uniqueId", resolvedSession.getUniqueId().toString()),
								Map.entry("eligibility", safe(resolvedSession.getProviderId(), unknown)),
								Map.entry("subject", safe(resolvedSession.getProviderSubject(), unknown)),
								Map.entry("session", safe(resolvedSession.getSessionId(), unknown)),
								Map.entry("ip", safe(resolvedSession.getIp(), unknown)),
								Map.entry("server", connection != null ? safe(connection.getServerId(), unknown) : unknown),
								Map.entry("connection", connection != null ? connection.getConnectionUniqueId().toString() : unknown),
								Map.entry("created", createdDateTime),
								Map.entry("createdDate", createdDate),
								Map.entry("createdDateTime", createdDateTime)
						)
				)
		));
	}

	@Definition("admin-session-end")
	@Command("identica admin session end <target>")
	public void end(@NotNull Actor sender, @Argument("target") String target) {
		Messages.Commands.Admin.Sessions messages = messagesProvider.get().getCommands().getAdmin().getSessions();
		Messages.Commands.Admin.Sessions.End endMessages = messages.getEnd();
		String unknown = messages.getUnknown();
		ResolvedTarget resolved = resolveTarget(sender, target, messages, "identica admin session end", endMessages.getNotFound());
		if (resolved == null) return;

		Optional<Session> session = sessionService.findByUniqueId(resolved.uniqueId()).join();
		if (session.isEmpty()) {
			sender.sendMessage(Serializer.serialize(sender, endMessages.getNotFound(), Map.of("target", target)));
			return;
		}

		Session resolvedSession = session.get();
		sessionService.close(SessionCloseRequest.builder()
				.uniqueId(resolvedSession.getUniqueId())
				.disconnect(true)
				.disconnectMessage(String.join("\n", endMessages.getDisconnect()))
				.build()).join();

		String username = resolveUsername(resolvedSession, unknown);
		sender.sendMessage(Serializer.serialize(sender, endMessages.getEnded(), Map.of(
				"username", username,
				"uniqueId", resolvedSession.getUniqueId().toString()
		)));
	}

	private List<Session> resolveSessions(List<UUID> ids) {
		List<Session> sessions = new ArrayList<>();
		for (UUID id : ids) {
			Optional<Session> session = sessionService.findByUniqueId(id).join();
			if (session.isEmpty()) {
				sessionService.close(id).join();
				continue;
			}
			sessions.add(session.get());
		}
		return sessions;
	}

	private ResolvedTarget resolveTarget(
			@NotNull Actor sender,
			@NotNull String target,
			@NotNull Messages.Commands.Admin.Sessions messages,
			@NotNull String command,
			String notFoundMessage
	) {
		UUID parsed = UniqueIdUtil.parseUniqueId(target);
		if (parsed != null) {
			return new ResolvedTarget(parsed);
		}

		Optional<Identity> player = identityService.find(target);
		if (player.isPresent()) {
			return new ResolvedTarget(player.get().getUniqueId());
		}

		List<Account> matches = accountPersistenceService.findByUsername(target);
		if (matches.isEmpty()) {
			sender.sendMessage(Serializer.serialize(sender, notFoundMessage, Map.of("target", target)));
			return null;
		}
		if (matches.size() > 1) {
			sendMultipleMatches(sender, target, matches, messages, command);
			return null;
		}

		return new ResolvedTarget(matches.getFirst().getUniqueId());
	}

	private void sendMultipleMatches(
			@NotNull Actor sender,
			@NotNull String target,
			@NotNull List<Account> matches,
			@NotNull Messages.Commands.Admin.Sessions messages,
			@NotNull String command
	) {
		Messages.Commands.Admin.Sessions.Multiple multiple = messages.getMultiple();

		List<String> entries = buildEntries(multiple.getEntry(), matches, account -> {
			String username = account.getUsername();
			Map<String, String> placeholders = new HashMap<>();
			placeholders.put("username", username);
			placeholders.put("uniqueId", account.getUniqueId().toString());
			placeholders.put("command", command);
			return new EntryData(placeholders, isPresent(username));
		});
		sender.sendMessage(Serializer.serialize(
				sender,
				Serializer.template(bodyTemplate(multiple.getBody()))
						.placeholders(Map.of(
								"target", target,
								"count", String.valueOf(matches.size())
						))
						.section("entries", section -> section
								.lines(entries)
								.onMissing(TemplateSection.MissingSectionPolicy.APPEND))
						.render()
		));
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
			if (!entry.isBlank()) entries.add(entry);
		}
		return entries;
	}

	private String resolveUsername(@NotNull Session session, String unknown) {
		String effective = session.getEffectiveUsername();
		if (effective != null && !effective.isBlank()) return effective;

		String original = session.getOriginalUsername();
		if (original != null && !original.isBlank()) return original;

		return unknown;
	}

	private String safe(String value, String unknown) {
		return value == null || value.isBlank() ? unknown : value;
	}

	private DateTimeFormatter resolveFormatter(DateTimePattern pattern, DateTimeFormatter fallback) {
		if (pattern == null) return fallback;
		return pattern.formatter(fallback);
	}

	private record ResolvedTarget(UUID uniqueId) {
	}

	private boolean isPresent(String value) {
		return value != null && !value.isBlank();
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
