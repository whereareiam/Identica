package me.whereareiam.identica.feature.verification.type.totp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Time-based one-time passwords as RFC 6238 defines them, with HMAC-SHA1. It holds no clock: callers
 * pass the time step, which {@link TotpClock} provides.
 */
public final class TotpCodec {
	private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
	private static final SecureRandom RANDOM = new SecureRandom();

	public static String generateSecret(int bytes) {
		byte[] raw = new byte[bytes];
		RANDOM.nextBytes(raw);
		return encodeBase32(raw);
	}

	/**
	 * Returns the time step an instant falls in: the number of whole periods since the Unix epoch.
	 */
	public static long timeStep(Instant instant, long periodSeconds) {
		return Math.floorDiv(instant.getEpochSecond(), periodSeconds);
	}

	/**
	 * Returns the newest time step around {@code currentStep} whose code is {@code input}, looking
	 * {@code allowedPastWindows} steps back and {@code allowedFutureWindows} steps ahead, or nothing
	 * when the input is no such code.
	 */
	public static OptionalLong matchingStep(
			String secret,
			String input,
			int digits,
			long currentStep,
			int allowedPastWindows,
			int allowedFutureWindows
	) {
		String normalizedInput = input == null ? "" : input.trim();
		if (!normalizedInput.matches("\\d{" + digits + "}")) return OptionalLong.empty();

		byte[] key = decodeBase32(secret);
		for (long step = currentStep + allowedFutureWindows; step >= currentStep - allowedPastWindows; step--)
			if (generateCode(key, step, digits).equals(normalizedInput)) return OptionalLong.of(step);

		return OptionalLong.empty();
	}

	/**
	 * Returns the code of a time step.
	 */
	public static String code(String secret, long step, int digits) {
		return generateCode(decodeBase32(secret), step, digits);
	}

	public static String buildOtpAuthUri(
			String issuer,
			String label,
			String secret,
			int digits,
			long periodSeconds
	) {
		String encodedIssuer = urlEncode(issuer);
		String encodedLabel = urlEncode(issuer + ":" + label);

		return "otpauth://totp/" + encodedLabel
				+ "?secret=" + secret
				+ "&issuer=" + encodedIssuer
				+ "&digits=" + digits
				+ "&period=" + periodSeconds;
	}

	private static String generateCode(byte[] key, long counter, int digits) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(key, "HmacSHA1"));
			byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
			int offset = hash[hash.length - 1] & 0x0F;
			int binary = ((hash[offset] & 0x7F) << 24)
					| ((hash[offset + 1] & 0xFF) << 16)
					| ((hash[offset + 2] & 0xFF) << 8)
					| (hash[offset + 3] & 0xFF);
			int otp = binary % (int) Math.pow(10, digits);

			return String.format(Locale.ROOT, "%0" + digits + "d", otp);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to generate TOTP", e);
		}
	}

	private static String encodeBase32(byte[] data) {
		StringBuilder output = new StringBuilder((data.length * 8 + 4) / 5);
		int buffer = 0;
		int bitsLeft = 0;
		for (byte datum : data) {
			buffer = (buffer << 8) | (datum & 0xFF);
			bitsLeft += 8;
			while (bitsLeft >= 5) {
				output.append(BASE32_ALPHABET[(buffer >> (bitsLeft - 5)) & 0x1F]);
				bitsLeft -= 5;
			}
		}

		if (bitsLeft > 0) output.append(BASE32_ALPHABET[(buffer << (5 - bitsLeft)) & 0x1F]);

		return output.toString();
	}

	private static byte[] decodeBase32(String secret) {
		String normalized = secret == null ? "" : secret.replace("=", "").replace(" ", "").toUpperCase(Locale.ROOT);
		if (normalized.isBlank()) return new byte[0];

		int buffer = 0;
		int bitsLeft = 0;
		ByteBuffer output = ByteBuffer.allocate(normalized.length() * 5 / 8);
		for (char character : normalized.toCharArray()) {
			int value = base32Value(character);
			if (value < 0) continue;

			buffer = (buffer << 5) | value;
			bitsLeft += 5;
			if (bitsLeft >= 8) {
				output.put((byte) ((buffer >> (bitsLeft - 8)) & 0xFF));
				bitsLeft -= 8;
			}
		}

		byte[] decoded = new byte[output.position()];
		output.flip();
		output.get(decoded);

		return decoded;
	}

	private static int base32Value(char character) {
		if (character >= 'A' && character <= 'Z') return character - 'A';
		if (character >= '2' && character <= '7') return character - '2' + 26;

		return -1;
	}

	private static String urlEncode(String input) {
		return URLEncoder.encode(input, StandardCharsets.UTF_8)
				.replace("+", "%20");
	}
}
