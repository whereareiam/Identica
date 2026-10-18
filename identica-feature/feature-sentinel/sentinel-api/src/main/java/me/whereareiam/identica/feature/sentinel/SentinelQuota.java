package me.whereareiam.identica.feature.sentinel;

import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelQuotaPolicy;
import me.whereareiam.identica.feature.sentinel.model.SentinelQuotaUsage;
import me.whereareiam.identica.feature.sentinel.type.SentinelScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A limit on how many of something a connection may have, such as accounts registered from one address, contributed
 * to the optional sentinel feature. Unlike a {@link SentinelDefinition}, which counts attempts for a while, a quota
 * counts what exists, which its owner keeps; the sentinel refuses the connection once the count reaches the limit.
 *
 * <pre>{@code
 * public final class AccountsPerAddressQuota implements SentinelQuota {
 *     public String id() { return "accounts-per-address"; }
 *     public SentinelScope[] scopes() { return new SentinelScope[]{SentinelScope.REGISTRATION}; }
 *     public SentinelQuotaPolicy policy(SentinelContext context) { return settings.policy(); }
 *     public SentinelQuotaUsage usage(SentinelContext context, SentinelQuotaPolicy policy) {
 *         return SentinelQuotaUsage.of(addresses.findRegistrationTimes(context.getIp(), null).size());
 *     }
 * }
 * }</pre>
 */
public interface SentinelQuota {
	/**
	 * Returns the provider whose connections this quota limits.
	 *
	 * @return the provider, or {@code null} for every provider
	 */
	default @Nullable String providerId() {
		return null;
	}

	/**
	 * Returns the stable identifier of this quota.
	 *
	 * @return the identifier
	 */
	@NotNull String id();

	/**
	 * Returns the points of a connection at which this quota is checked.
	 *
	 * @return the scopes
	 */
	@NotNull SentinelScope[] scopes();

	/**
	 * Resolves the limit for a connection.
	 *
	 * @param context the connection and its identifiers
	 * @return the policy; an inactive policy limits nothing and the usage is not counted
	 */
	@NotNull SentinelQuotaPolicy policy(@NotNull SentinelContext context);

	/**
	 * Counts how much of the quota a connection already uses.
	 *
	 * @param context the connection and its identifiers
	 * @param policy  the active policy {@link #policy} returned
	 * @return the usage
	 */
	@NotNull SentinelQuotaUsage usage(@NotNull SentinelContext context, @NotNull SentinelQuotaPolicy policy);

	/**
	 * Returns the priority used to choose between refusals of several sentinels.
	 *
	 * @return the priority; a higher one wins
	 */
	default int priority() {
		return 0;
	}
}
