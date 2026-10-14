package me.whereareiam.identica.pipeline.state;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Pipeline-State Reference")
class PipelineStateReferenceTest {
	private static final String OWNER_ADDRESS = "127.0.0.1";
	private static final String OTHER_ADDRESS = "127.0.0.2";

	@DisplayName("A reference without a username names no connection")
	@Test
	void emptyReferenceRequiresUsername() {
		assertTrue(PipelineStateReference.of(null, OWNER_ADDRESS).isEmpty());
		assertTrue(PipelineStateReference.of(" ", OWNER_ADDRESS).isEmpty());
	}

	@DisplayName("A reference names the connection by its username without regard to case and its address")
	@Test
	void referenceIgnoresTheCaseOfTheUsername() {
		assertEquals(PipelineStateReference.of("Alice", OWNER_ADDRESS), PipelineStateReference.of("alice", OWNER_ADDRESS));
		assertNotEquals(PipelineStateReference.of("Alice", OWNER_ADDRESS), PipelineStateReference.of("Alice", OTHER_ADDRESS));
	}

	@DisplayName("A reference keeps the username the connection was identified by when the identity is renamed")
	@Test
	void referenceFollowsTheConnectionUsername() {
		ConnectionIdentity identity = new ConnectionIdentity(UUID.randomUUID(), "Alice", OWNER_ADDRESS);
		identity.setUsername("Alice_2");

		assertEquals(PipelineStateReference.of("Alice", OWNER_ADDRESS), PipelineStateReference.from(identity));
	}
}
