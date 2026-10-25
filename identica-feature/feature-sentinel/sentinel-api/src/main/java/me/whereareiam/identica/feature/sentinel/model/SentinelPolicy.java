package me.whereareiam.identica.feature.sentinel.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.model.message.PlayerMessage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Configurable policy used to evaluate a sentinel definition.
 */
@Getter
@Setter
@ToString
public class SentinelPolicy {
	private boolean enabled;
	private int maxAttempts;
	private Lockout lockout = new Lockout();
	private Warning warning = new Warning();

	/**
	 * Returns whether the policy has enough data to actively participate.
	 *
	 * @return {@code true} when the policy can evaluate attempts
	 */
	public boolean isActive() {
		return enabled
				&& maxAttempts > 0
				&& lockout != null
				&& isPositive(lockout.getDuration())
				&& ((lockout.isEnabled()) || (warning != null && warning.isEnabled()));
	}

	/**
	 * Creates a fully disabled sentinel policy.
	 *
	 * @return disabled sentinel policy
	 */
	public static @NotNull SentinelPolicy disabled() {
		SentinelPolicy policy = new SentinelPolicy();
		policy.setEnabled(false);
		policy.setMaxAttempts(0);
		if (policy.getLockout() != null) {
			policy.getLockout().setEnabled(false);
			policy.getLockout().setDuration(Duration.ZERO);
		}
		if (policy.getWarning() != null) {
			policy.getWarning().setEnabled(false);
			policy.getWarning().setThresholdPercentage(0);
		}

		return policy;
	}

	private boolean isPositive(@Nullable Duration duration) {
		return duration != null && !duration.isZero() && !duration.isNegative();
	}

	/**
	 * Lockout behavior applied once the attempt threshold is reached.
	 */
	@Getter
	@Setter
	@ToString
	public static class Lockout {
		private boolean enabled = true;
		private @Nullable Duration duration;
		private transient @Nullable BiFunction<SentinelContext, Long, PlayerMessage> messageSupplier;
		private transient @Nullable BiConsumer<SentinelContext, SentinelDecision> customAction;
	}

	/**
	 * Warning behavior emitted before a lockout is reached.
	 */
	@Getter
	@Setter
	@ToString
	public static class Warning {
		private boolean enabled = true;
		private int thresholdPercentage;
		private transient @Nullable BiFunction<SentinelContext, Integer, PlayerMessage> messageSupplier;
		private transient @Nullable BiConsumer<SentinelContext, SentinelDecision> customAction;
	}
}
