package me.whereareiam.identica.testing.fixture;

import me.whereareiam.identica.testing.environment.ProxyConfiguration;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * An authenticator app holding the secret a player was shown, computing time-based codes the way such apps do
 * (RFC 6238 with HMAC-SHA1). It is written independently of Identica, so a journey fails when Identica's codes
 * differ from what a real app would show.
 *
 * <p>Identica accepts each code once, so a journey that types a second code takes {@link #nextCode()}. The app
 * changes codes every {@link #PERIOD}, which a network gets from {@link ShortPeriod}.</p>
 */
public final class Authenticator {
	/** How long a code is shown: short, so a journey that needs the next code waits for it briefly. */
	public static final Duration PERIOD = Duration.ofSeconds(5);
	/**
	 * How many periods back Identica still accepts a code: a minute, longer than a journey takes between using
	 * a code and typing it again, so only Identica's record of the used code can refuse it.
	 */
	private static final int PAST_WINDOWS = 12;
	private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

	private final byte[] key;
	private final int digits;
	private long shown = Long.MIN_VALUE;

	private Authenticator(byte[] key, int digits) {
		this.key = key;
		this.digits = digits;
	}

	/**
	 * Adds a secret as an app does: six-digit codes that change every {@link #PERIOD}.
	 *
	 * @param secret Base32 secret as the player read it
	 */
	public static Authenticator of(String secret) {
		return new Authenticator(decode(secret), 6);
	}

	/**
	 * Returns the code the app shows now.
	 */
	public String code() {
		return show(window(Instant.now()));
	}

	/**
	 * Returns the code after the one this app returned last, waiting until the app shows it: at most one
	 * {@link #PERIOD}.
	 */
	public String nextCode() {
		long next = shown + 1;
		Duration remaining = Duration.between(Instant.now(), Instant.ofEpochSecond(next * PERIOD.toSeconds()));
		if (!remaining.isNegative()) {
			try {
				Thread.sleep(remaining.plusMillis(50));
			} catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Interrupted waiting for the next code", interrupted);
			}
		}

		return code();
	}

	/**
	 * Returns the code an app whose clock runs one period ahead shows now, which Identica accepts as clock drift.
	 */
	public String codeAhead() {
		return show(window(Instant.now()) + 1);
	}

	/**
	 * Returns the code this app returned last, as someone who saw the player type it would type it again.
	 */
	public String usedCode() {
		if (shown == Long.MIN_VALUE) throw new IllegalStateException("The app has not shown a code yet");
		return code(shown);
	}

	/**
	 * Returns a well-formed code the app does not show now and did not show in any window Identica still accepts.
	 */
	public String wrongCode() {
		long now = window(Instant.now());
		Set<String> nearby = new HashSet<>();
		for (long offset = -PAST_WINDOWS - 1; offset <= 3; offset++)
			nearby.add(code(now + offset));

		int modulus = (int) Math.pow(10, digits);
		int candidate = Integer.parseInt(code(now));
		String wrong;
		do {
			candidate = (candidate + 1) % modulus;
			wrong = format(candidate);
		} while (nearby.contains(wrong));

		return wrong;
	}

	private String show(long window) {
		shown = Math.max(shown, window);
		return code(window);
	}

	private long window(Instant instant) {
		return instant.getEpochSecond() / PERIOD.toSeconds();
	}

	private String code(long window) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(key, "HmacSHA1"));
			byte[] hash = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(window).array());
			int offset = hash[hash.length - 1] & 0x0F;
			int binary = ((hash[offset] & 0x7F) << 24)
					| ((hash[offset + 1] & 0xFF) << 16)
					| ((hash[offset + 2] & 0xFF) << 8)
					| (hash[offset + 3] & 0xFF);
			return format(binary % (int) Math.pow(10, digits));
		} catch (GeneralSecurityException failure) {
			throw new IllegalStateException("HMAC-SHA1 is not available", failure);
		}
	}

	private String format(int code) {
		return String.format(Locale.ROOT, "%0" + digits + "d", code);
	}

	/**
	 * Gives a network the app's {@link #PERIOD}, and a drift window into the past long enough that a used code
	 * is still inside it when a journey types it again.
	 */
	public static final class ShortPeriod implements Consumer<ProxyConfiguration> {
		@Override
		public void accept(ProxyConfiguration proxy) {
			proxy.verification().getTotp().setPeriod(PERIOD);
			proxy.verification().getTotp().setAllowedPastWindows(PAST_WINDOWS);
		}
	}

	private static byte[] decode(String secret) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		int buffer = 0;
		int bits = 0;
		for (char character : secret.replace(" ", "").replace("=", "").toUpperCase(Locale.ROOT).toCharArray()) {
			int value = ALPHABET.indexOf(character);
			if (value < 0) throw new IllegalArgumentException("Not a Base32 secret: " + secret);

			buffer = (buffer << 5) | value;
			bits += 5;
			if (bits < 8) continue;

			bytes.write((buffer >> (bits - 8)) & 0xFF);
			bits -= 8;
		}

		return bytes.toByteArray();
	}
}
