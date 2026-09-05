package me.whereareiam.identica.feature.sentinel;

import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.connection.attempt.ConnectionResumeAttemptEvent;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.feature.sentinel.model.SentinelDecision;
import me.whereareiam.identica.feature.sentinel.SentinelService;
import me.whereareiam.identica.feature.sentinel.type.SentinelScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Connection Attempt Sentinel Lifecycle")
class ConnectionAttemptSentinelLifecycleTest {
	@DisplayName("Resume attempt is denied when a sentinel returns a denying decision")
	@Test
	void resumeAttemptIsDeniedWhenSentinelReturnsDeny() {
		SentinelService sentinelService = mock(SentinelService.class);
		EventManager eventManager = mock(EventManager.class);
		when(sentinelService.evaluate(eq(SentinelScope.RESUME), any()))
				.thenReturn(Optional.of(SentinelDecision.limited(null, 30, "blocked")));

		ConnectionAttemptSentinelLifecycle lifecycle = new ConnectionAttemptSentinelLifecycle(sentinelService, mock(me.whereareiam.identica.pipeline.state.PipelineStateStore.class));
		ConnectionResumeAttemptEvent event = new ConnectionResumeAttemptEvent(
				UUID.randomUUID(),
				UUID.randomUUID(),
				"player",
				"127.0.0.1"
		);

		lifecycle.onConnectionAttempt(event);

		assertNotNull(event.getDecision());
		assertEquals(ConnectionDecision.Status.DENY, event.getDecision().getStatus());
		assertEquals("blocked", event.getDecision().getMessage());
	}
}
