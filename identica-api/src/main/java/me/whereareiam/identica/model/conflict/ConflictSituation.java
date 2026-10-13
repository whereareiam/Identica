package me.whereareiam.identica.model.conflict;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a conflict rule is matched against: both sides, what brought the conflict up, and how the arriving player
 * reached the proxy.
 *
 * @param <T> what brings conflicts of the kind up
 */
@Getter
@ToString
@Builder(toBuilder = true)
public class ConflictSituation<T extends Enum<T>> {
	/**
	 * The side that wants what the other side has.
	 */
	private final @NotNull ConflictParty arriving;

	/**
	 * The side that has it.
	 */
	private final @NotNull ConflictParty holder;

	/**
	 * What brought the conflict up.
	 */
	private final @NotNull T trigger;

	/**
	 * Why the arriving player's provider was picked for the connection, or {@code null} when not known.
	 */
	private final @Nullable ProviderClaimOrigin arrivedVia;
}
