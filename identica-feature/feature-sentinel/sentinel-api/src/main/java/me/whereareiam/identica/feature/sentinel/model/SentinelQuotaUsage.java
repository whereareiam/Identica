package me.whereareiam.identica.feature.sentinel.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

/**
 * How much of a quota a connection already uses, and when that drops below the limit by itself.
 */
@Getter
@ToString
@RequiredArgsConstructor
public class SentinelQuotaUsage {
	/**
	 * How many of what the quota limits already count.
	 */
	private final long used;
	/**
	 * When enough of them stop counting to fall below the limit, in epoch milliseconds; {@code 0} when that never
	 * happens by itself.
	 */
	private final long freesAt;

	/**
	 * Creates a usage that never drops by itself.
	 *
	 * @param used how many already count
	 * @return the usage
	 */
	public static @NotNull SentinelQuotaUsage of(long used) {
		return new SentinelQuotaUsage(used, 0L);
	}
}
