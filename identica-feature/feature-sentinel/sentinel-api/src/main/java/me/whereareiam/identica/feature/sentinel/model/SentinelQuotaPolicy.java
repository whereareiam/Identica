package me.whereareiam.identica.feature.sentinel.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * The limit of a {@link me.whereareiam.identica.feature.sentinel.SentinelQuota} and how its refusal reads.
 */
@Getter
@Setter
@ToString
public class SentinelQuotaPolicy {
	private boolean enabled;
	/**
	 * How many of what the quota limits a connection may reach; reaching it refuses the next one.
	 */
	private int limit;
	/**
	 * Words the refusal from the connection and its usage; {@code null} or a blank result refuses without text.
	 */
	private @Nullable BiFunction<SentinelContext, SentinelQuotaUsage, String> messageSupplier;

	/**
	 * Returns whether the quota limits anything.
	 *
	 * @return {@code true} when it is enabled with a positive limit
	 */
	public boolean isActive() {
		return enabled && limit > 0;
	}

	/**
	 * Creates a policy that limits nothing.
	 *
	 * @return a disabled policy
	 */
	public static @NotNull SentinelQuotaPolicy disabled() {
		return new SentinelQuotaPolicy();
	}
}
