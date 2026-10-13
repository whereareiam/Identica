package me.whereareiam.identica.engine.step;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.username.UsernameAssigner;
import me.whereareiam.identica.model.identity.username.UsernameArrival;
import me.whereareiam.identica.model.identity.username.UsernameAssignment;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.model.registration.RegistrationContext;
import me.whereareiam.identica.type.identity.AccountUsernameSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Username Availability Step")
class UsernameAvailabilityStepTest {
	private static final UUID ARRIVING = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private final UsernameAssigner assigner = mock(UsernameAssigner.class);
	private final UsernameAvailabilityStep step = new UsernameAvailabilityStep(assigner);

	@Test
	@DisplayName("Refuses a registration the username conflict rules refuse, with their message")
	void refusesARefusedUsername() {
		when(assigner.preview(any())).thenReturn(UsernameAssignment.refuse("taken alice"));

		StepResult result = step.execute(context("alice", "chosen")).join();

		assertEquals(StepResult.StepStatus.DENIED, result.getStatus());
		assertEquals("taken alice", result.getMessage());
	}

	@Test
	@DisplayName("Lets a registration through that the rules give a username, as a first join and without writing")
	void passesAnAssignedUsername() {
		when(assigner.preview(any())).thenReturn(UsernameAssignment.of("alice", "alice", AccountUsernameSource.PROVIDER));

		StepResult result = step.execute(context("alice", "chosen")).join();

		assertEquals(StepResult.StepStatus.CONTINUE, result.getStatus());
		ArgumentCaptor<UsernameArrival> arrival = ArgumentCaptor.forClass(UsernameArrival.class);
		verify(assigner).preview(arrival.capture());
		verify(assigner, never()).assign(any());
		assertTrue(arrival.getValue().isFirstJoin());
		assertEquals("chosen", arrival.getValue().getProviderId());
		assertEquals("alice", arrival.getValue().getUsername());
	}

	@Test
	@DisplayName("Lets a registration through before its provider is known")
	void passesWithoutAProvider() {
		RegistrationContext context = RegistrationContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(ARRIVING, "alice", "127.0.0.1"))
				.build();

		StepResult result = step.execute(context).join();

		assertEquals(StepResult.StepStatus.CONTINUE, result.getStatus());
		verify(assigner, never()).preview(any());
	}

	private static RegistrationContext context(String username, String providerId) {
		RegistrationContext context = RegistrationContext.builder()
				.connectionUniqueId(UUID.randomUUID())
				.identity(new ConnectionIdentity(ARRIVING, username, "127.0.0.1"))
				.build();
		context.setProvider(ProviderContext.builder()
				.providerId(providerId)
				.providerSubject("subject")
				.providerUsername(username)
				.build());

		return context;
	}
}
