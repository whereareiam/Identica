package me.whereareiam.identica.platform.common.stage;

import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.pipeline.prepare.PrepareStateStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Prepare state store that keeps decisions in maps, the way the default store does without expiry.
 */
public final class InMemoryPrepareStateStore implements PrepareStateStore {
	public final Map<String, PrepareDecision> byConnectionKey = new HashMap<>();
	public final Map<UUID, PrepareDecision> byUniqueId = new HashMap<>();
	private final Map<UUID, String> keysByUniqueId = new HashMap<>();

	@Override
	public void put(@NotNull String connectionKey, @NotNull PrepareDecision decision) {
		byConnectionKey.put(connectionKey, decision);
	}

	@Override
	public void put(@NotNull UUID uniqueId, @Nullable String connectionKey, @NotNull PrepareDecision decision) {
		byUniqueId.put(uniqueId, decision);
		if (connectionKey == null || connectionKey.isBlank()) return;

		keysByUniqueId.put(uniqueId, connectionKey);
		put(connectionKey, decision);
	}

	@Override
	public @NotNull Optional<PrepareDecision> peek(@NotNull String connectionKey) {
		return Optional.ofNullable(byConnectionKey.get(connectionKey));
	}

	@Override
	public @NotNull Optional<PrepareDecision> peek(@NotNull UUID uniqueId) {
		return Optional.ofNullable(byUniqueId.get(uniqueId));
	}

	@Override
	public boolean clear(@NotNull UUID uniqueId) {
		boolean removed = byUniqueId.remove(uniqueId) != null;
		String connectionKey = keysByUniqueId.remove(uniqueId);
		if (connectionKey != null) removed = byConnectionKey.remove(connectionKey) != null || removed;

		return removed;
	}

	@Override
	public boolean clearIf(
			@Nullable UUID uniqueId,
			@Nullable String connectionKey,
			@NotNull Predicate<PrepareDecision> condition
	) {
		boolean removed = uniqueId != null && removeIf(byUniqueId, uniqueId, condition);
		if (connectionKey != null) removed = removeIf(byConnectionKey, connectionKey, condition) || removed;

		return removed;
	}

	private static <K> boolean removeIf(Map<K, PrepareDecision> decisions, K key, Predicate<PrepareDecision> condition) {
		PrepareDecision decision = decisions.get(key);
		return decision != null && condition.test(decision) && decisions.remove(key, decision);
	}
}
