package me.whereareiam.identica.feature.sentinel;

import me.whereareiam.identica.event.connection.UsernameHeldEvent;
import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.type.ContestedConnectionSentinelDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("Contested Login Sentinel Lifecycle")
class ContestedLoginSentinelLifecycleTest {
	@DisplayName("Records a refused connection as an attempt of its address")
	@Test
	void recordsTheRefusal() {
		SentinelService service = mock(SentinelService.class);
		ContestedLoginSentinelLifecycle lifecycle = new ContestedLoginSentinelLifecycle(service);

		lifecycle.onUsernameHeld(new UsernameHeldEvent(UUID.randomUUID(), "Alice", "203.0.113.10"));

		ArgumentCaptor<SentinelContext> context = ArgumentCaptor.forClass(SentinelContext.class);
		verify(service).record(eq(ContestedConnectionSentinelDefinition.ID), context.capture());
		assertEquals("203.0.113.10", context.getValue().getIp());
		assertEquals("Alice", context.getValue().getUsername());
	}
}
