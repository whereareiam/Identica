package me.whereareiam.identica.platform.common.checkpoint;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlatformLoadingScreenTest {
	private static final UUID ALICE = UUID.randomUUID();

	private final PlatformLoadingScreen loadingScreen = new PlatformLoadingScreen();

	@Test
	void holdsAPlayerFromHeldUntilReleased() {
		assertFalse(loadingScreen.holds(ALICE));

		loadingScreen.held(ALICE);
		assertTrue(loadingScreen.holds(ALICE));

		loadingScreen.released(ALICE);
		assertFalse(loadingScreen.holds(ALICE));
	}
}
