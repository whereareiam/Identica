package me.whereareiam.identica.model.identity;

import lombok.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Canonical holder for the UUID roles associated with a logical identity flow.
 *
 * <p>The same player may have multiple UUIDs visible across the connection
 * lifecycle. This type keeps those roles explicit so callers do not treat the
 * live connection UUID, the UUID the client claims, the observed platform UUID,
 * and the resolved account UUID as interchangeable.</p>
 *
 * <pre>{@code
 * IdentityReference reference = IdentityReference.builder()
 *         .connectionUniqueId(connectionUniqueId)
 *         .observedUniqueId(platformUniqueId)
 *         .accountUniqueId(accountUniqueId)
 *         .build();
 * }</pre>
 */
@Getter
@Setter
@Builder(toBuilder = true)
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class IdentityReference {
	private @Nullable UUID connectionUniqueId;
	/**
	 * UUID the client sent together with its username when it started logging in. The client reports it itself and
	 * nothing has verified it; it is {@code null} for clients older than 1.19.1 and on platforms that do not expose it.
	 */
	private @Nullable UUID claimedUniqueId;
	private @Nullable UUID observedUniqueId;
	private @Nullable UUID accountUniqueId;

	/**
	 * Creates a reference that only carries the resolved account UUID.
	 *
	 * @param accountUniqueId resolved account UUID
	 * @return reference populated with the account UUID
	 */
	public static @NotNull IdentityReference account(@Nullable UUID accountUniqueId) {
		return IdentityReference.builder()
				.accountUniqueId(accountUniqueId)
				.build();
	}

	/**
	 * Returns whether every UUID role is currently absent.
	 *
	 * @return {@code true} when no UUID role is populated
	 */
	public boolean isEmpty() {
		return connectionUniqueId == null
				&& claimedUniqueId == null
				&& observedUniqueId == null
				&& accountUniqueId == null;
	}
}
