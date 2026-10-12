package me.whereareiam.identica.feature.verification.type.totp;

import org.jetbrains.annotations.NotNull;

import java.time.InstantSource;
import java.util.Objects;

/**
 * The clock TOTP codes are checked against, which tests replace with a fixed instant.
 */
public final class TotpClock {
	private final @NotNull InstantSource source;

	public TotpClock(@NotNull InstantSource source) {
		this.source = Objects.requireNonNull(source, "source");
	}

	/**
	 * Returns the time step the clock is in now.
	 *
	 * @param periodSeconds length of a time step in seconds
	 */
	public long currentStep(long periodSeconds) {
		return TotpCodec.timeStep(source.instant(), periodSeconds);
	}
}
