package me.whereareiam.identica.feature.sentinel;

import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelPolicy;
import me.whereareiam.identica.feature.sentinel.type.SentinelKey;
import me.whereareiam.identica.feature.sentinel.type.SentinelMode;
import me.whereareiam.identica.feature.sentinel.type.SentinelScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A named rate-limit policy contributed to the optional sentinel feature.
 */
public interface SentinelDefinition {
	/** @return owning provider, or null for a global connection policy */
	default @Nullable String providerId() {
		return null;
	}

	/** @return stable identifier used for counters and explicit recording */
	@NotNull String id();

	/** @return connection operations in which this policy participates */
	@NotNull SentinelScope[] scopes();

	/**
	 * Selects whether an operation records an attempt or only checks a lockout.
	 * @param scope connection operation being evaluated
	 * @return evaluation mode
	 */
	@NotNull SentinelMode modeFor(@NotNull SentinelScope scope);

	/**
	 * Resolves the policy for an attempt, including optional messages and actions.
	 * @param context connection and account identifiers
	 * @return policy; a disabled policy does not participate
	 */
	@NotNull SentinelPolicy policy(@NotNull SentinelContext context);

	/**
	 * Resolves the counter identity. A blank key excludes this attempt.
	 * @param context connection and account identifiers
	 * @return key combining the IP and available identity by default
	 */
	default @NotNull SentinelKey key(@NotNull SentinelContext context) {
		return SentinelKey.ipAndIdentity(context);
	}

	/** @return priority used to choose between decisions with equal denial status */
	default int priority() {
		return 0;
	}
}
