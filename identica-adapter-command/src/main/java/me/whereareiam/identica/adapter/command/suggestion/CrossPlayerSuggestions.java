package me.whereareiam.identica.adapter.command.suggestion;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.config.Commands;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.keystone.Actor;
import org.incendo.cloud.context.CommandContext;
import org.incendo.cloud.context.CommandInput;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CrossPlayerSuggestions implements SuggestionProvider<Actor> {
	public static final @NotNull String KEY = "crossPlayer";

	private final @NotNull SessionService sessionService;
	private final @NotNull Provider<Commands> commandsProvider;

	@Override
	public @NotNull CompletableFuture<? extends Iterable<? extends Suggestion>> suggestionsFuture(
			@NotNull CommandContext<Actor> context,
			@NotNull CommandInput input
	) {
		String prefix = normalizePrefix(input);
		int limit = commandsProvider.get().getBehavior().getSuggestions().getPlayerLimit();
		if (limit <= 0) return CompletableFuture.completedFuture(List.of());

		return resolveSessionCandidates(limit)
				.exceptionally(ignored -> List.of())
				.thenApply(remote -> filterCandidates(remote, prefix, limit))
				.thenApply(CrossPlayerSuggestions::mapSuggestions);
	}

	private CompletableFuture<List<String>> resolveSessionCandidates(int limit) {
		return sessionService.list(1, limit)
				.thenCompose(page -> loadSessionCandidates(page, limit));
	}

	private CompletableFuture<List<String>> loadSessionCandidates(
			@NotNull SessionService.Page page,
			int limit
	) {
		if (page.entries().isEmpty())
			return CompletableFuture.completedFuture(List.of());

		List<CompletableFuture<Optional<Session>>> lookups = new ArrayList<>();
		for (String sessionId : page.entries()) {
			CompletableFuture<Optional<Session>> future = sessionService.findBySessionId(sessionId)
					.exceptionally(ignored -> Optional.empty());
			lookups.add(future);
		}

		CompletableFuture<Void> all = CompletableFuture.allOf(lookups.toArray(new CompletableFuture[0]));
		return all.thenApply(ignored -> {
			List<String> candidates = new ArrayList<>();
			for (CompletableFuture<Optional<Session>> lookup : lookups) {
				Optional<Session> session = lookup.join();
				if (session.isEmpty()) continue;

				String username = resolveUsername(session.get());
				if (username == null || username.isBlank()) {
					candidates.add(session.get().getUniqueId().toString());
					continue;
				}

				candidates.add(username);
				if (candidates.size() >= limit)
					break;
			}
			return candidates;
		});
	}

	private List<String> filterCandidates(
			@NotNull List<String> source,
			@NotNull String prefix,
			int limit
	) {
		Map<String, String> merged = new LinkedHashMap<>();
		for (String value : source) {
			if (merged.size() >= limit) break;
			addCandidate(merged, value, prefix);
		}
		return new ArrayList<>(merged.values());
	}

	private void addCandidate(
			@NotNull Map<String, String> target,
			@Nullable String value,
			@NotNull String prefix
	) {
		if (value == null || value.isBlank()) return;
		String lower = value.toLowerCase(Locale.ROOT);

		if (!prefix.isEmpty() && !lower.startsWith(prefix)) return;
		target.putIfAbsent(lower, value);
	}

	private static Iterable<Suggestion> mapSuggestions(@NotNull List<String> values) {
		List<Suggestion> suggestions = new ArrayList<>();
		for (String value : values) {
			suggestions.add(Suggestion.suggestion(value));
		}

		return suggestions;
	}

	private @Nullable String resolveUsername(@NotNull Session session) {
		String effective = session.getEffectiveUsername();
		if (effective != null && !effective.isBlank())
			return effective;

		String original = session.getOriginalUsername();
		if (original != null && !original.isBlank())
			return original;

		return null;
	}

	private static @NotNull String normalizePrefix(@NotNull CommandInput input) {
		String token = input.lastRemainingToken();
		if (token.isBlank()) token = input.remainingInput();

		return token.trim().toLowerCase(Locale.ROOT);
	}
}
