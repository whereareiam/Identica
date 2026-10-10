package me.whereareiam.identica.platform.bungeecord.adapter.profile;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("BungeeCord Profile Prepare Adapter")
class BungeeCordProfilePrepareAdapterTest {
	@Test
	@DisplayName("Updates both Bungee pending connection UUID fields")
	void applyUniqueIdRewriteUpdatesUniqueIdAndRewriteId() {
		UUID originalUniqueId = UUID.randomUUID();
		UUID originalRewriteId = UUID.randomUUID();
		UUID rewrittenUniqueId = UUID.randomUUID();
		TestPendingConnectionState state = new TestPendingConnectionState(originalUniqueId, originalRewriteId);

		boolean updated = BungeeCordProfilePrepareAdapter.applyUniqueIdRewrite(state, rewrittenUniqueId);

		assertTrue(updated);
		assertEquals(rewrittenUniqueId, state.uniqueId);
		assertEquals(rewrittenUniqueId, state.rewriteId);
	}

	@RequiredArgsConstructor
	private static final class TestPendingConnectionState {
		private final UUID uniqueId;
		private final UUID rewriteId;
	}
}
