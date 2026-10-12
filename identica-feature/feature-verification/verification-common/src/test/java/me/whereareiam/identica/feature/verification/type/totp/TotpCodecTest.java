package me.whereareiam.identica.feature.verification.type.totp;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TotpCodecTest {
	/** The SHA-1 secret of RFC 6238's test vectors, {@code "12345678901234567890"}, in Base32. */
	private static final String SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

	@Test
	void computesTheCodesOfRfc6238() {
		assertEquals("94287082", TotpCodec.code(SECRET, TotpCodec.timeStep(Instant.ofEpochSecond(59), 30), 8));
		assertEquals("07081804", TotpCodec.code(SECRET, TotpCodec.timeStep(Instant.ofEpochSecond(1111111109), 30), 8));
		assertEquals("287082", TotpCodec.code(SECRET, 1, 6));
	}

	@Test
	void returnsTheStepACodeMatchesWithinTheDriftWindow() {
		long now = 1_000;

		assertEquals(OptionalLong.of(now), TotpCodec.matchingStep(SECRET, TotpCodec.code(SECRET, now, 6), 6, now, 1, 1));
		assertEquals(OptionalLong.of(now - 1), TotpCodec.matchingStep(SECRET, TotpCodec.code(SECRET, now - 1, 6), 6, now, 1, 1));
		assertEquals(OptionalLong.of(now + 1), TotpCodec.matchingStep(SECRET, " " + TotpCodec.code(SECRET, now + 1, 6) + " ", 6, now, 1, 1));
	}

	@Test
	void matchesNoStepOutsideTheDriftWindowOrForMalformedInput() {
		long now = 1_000;

		assertEquals(OptionalLong.empty(), TotpCodec.matchingStep(SECRET, TotpCodec.code(SECRET, now - 2, 6), 6, now, 1, 1));
		assertEquals(OptionalLong.empty(), TotpCodec.matchingStep(SECRET, TotpCodec.code(SECRET, now + 2, 6), 6, now, 1, 1));
		assertEquals(OptionalLong.empty(), TotpCodec.matchingStep(SECRET, "12345", 6, now, 1, 1));
		assertEquals(OptionalLong.empty(), TotpCodec.matchingStep(SECRET, "abcdef", 6, now, 1, 1));
		assertEquals(OptionalLong.empty(), TotpCodec.matchingStep(SECRET, null, 6, now, 1, 1));
	}
}
