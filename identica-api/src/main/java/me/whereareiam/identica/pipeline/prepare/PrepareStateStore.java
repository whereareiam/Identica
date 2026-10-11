package me.whereareiam.identica.pipeline.prepare;

import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Store for staged connection preparation results.
 *
 * <p>This store allows platform adapters to bridge multiple connection events
 * that participate in the same logical preparation flow.</p>
 */
@SuppressWarnings("unused")
public interface PrepareStateStore {
	/**
	 * Stores a preparation decision by connection key.
	 *
	 * @param connectionKey connection-scoped key
	 * @param decision preparation decision
	 */
	void put(@NotNull String connectionKey, @NotNull PrepareDecision decision);

	/**
	 * Stores a preparation decision by final connection/profile unique id.
	 *
	 * @param uniqueId unique id
	 * @param connectionKey optional connection key associated with the same decision
	 * @param decision preparation decision
	 */
	void put(@NotNull UUID uniqueId, @Nullable String connectionKey, @NotNull PrepareDecision decision);

	/**
	 * Returns a preparation decision for the connection key without clearing it.
	 *
	 * @param connectionKey connection key
	 * @return optional preparation decision
	 */
	@NotNull Optional<PrepareDecision> peek(@NotNull String connectionKey);

	/**
	 * Returns a preparation decision for the unique id without clearing it.
	 *
	 * @param uniqueId unique id
	 * @return optional preparation decision
	 */
	@NotNull Optional<PrepareDecision> peek(@NotNull UUID uniqueId);

	/**
	 * Clears staged preparation state for the unique id.
	 *
	 * @param uniqueId unique id
	 * @return {@code true} if a decision was cleared
	 */
	boolean clear(@NotNull UUID uniqueId);

	/**
	 * Clears the decisions stored under the unique id and under the connection key that match the condition.
	 * Each entry is checked and removed in one step, so a decision stored in between is never cleared. A decision
	 * that does not match stays stored, with its expiry unchanged.
	 *
	 * @param uniqueId unique id, if known
	 * @param connectionKey connection key, if known
	 * @param condition what a stored decision must match to be cleared
	 * @return {@code true} if a decision was cleared
	 */
	boolean clearIf(
			@Nullable UUID uniqueId,
			@Nullable String connectionKey,
			@NotNull Predicate<PrepareDecision> condition
	);
}
