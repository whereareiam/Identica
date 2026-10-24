package me.whereareiam.identica.engine.pipeline.scenario;

import me.whereareiam.identica.common.event.EventController;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.identity.IdentityDetachedEvent;
import me.whereareiam.identica.event.scenario.authentication.AuthenticationRequiredEvent;
import me.whereareiam.identica.event.scenario.authentication.AuthenticationResolvedEvent;
import me.whereareiam.identica.event.scenario.migration.MigrationRequiredEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Default Pending Scenario Service")
class DefaultPendingScenarioServiceTest {
	private final EventController eventManager = new EventController();
	private final DefaultPendingScenarioService service = new DefaultPendingScenarioService(eventManager);
	private final UUID connectionUniqueId = UUID.randomUUID();
	private final UUID accountUniqueId = UUID.randomUUID();

	@DisplayName("A connection is pending from the required event until the resolved event")
	@Test
	void pendingBetweenRequiredAndResolved() {
		AuthContext context = authContext();

		eventManager.call(new AuthenticationRequiredEvent(connectionUniqueId, accountUniqueId, context, false, 0L));
		assertEquals(Optional.of(PipelineType.AUTHENTICATION), service.find(connectionUniqueId));

		eventManager.call(new AuthenticationResolvedEvent(connectionUniqueId, accountUniqueId, context, ScenarioResolution.COMPLETED, true));
		assertFalse(service.isPending(connectionUniqueId));
	}

	@DisplayName("Resolving one scenario keeps the scenario that already followed it")
	@Test
	void resolvingKeepsTheFollowingScenario() {
		AuthContext context = authContext();
		MigrationContext migration = MigrationContext.builder()
				.connectionUniqueId(connectionUniqueId)
				.identity(new ConnectionIdentity(accountUniqueId, "PlayerOne", "127.0.0.1"))
				.targetProviderId("premium")
				.build();

		eventManager.call(new AuthenticationRequiredEvent(connectionUniqueId, accountUniqueId, context, false, 0L));
		eventManager.call(new MigrationRequiredEvent(connectionUniqueId, accountUniqueId, migration, false, 0L));
		eventManager.call(new AuthenticationResolvedEvent(connectionUniqueId, accountUniqueId, context, ScenarioResolution.COMPLETED, true));

		assertEquals(Optional.of(PipelineType.MIGRATION), service.find(connectionUniqueId));
	}

	@DisplayName("A connection that leaves is no longer pending")
	@Test
	void detachedConnectionIsNotPending() {
		eventManager.call(new AuthenticationRequiredEvent(connectionUniqueId, accountUniqueId, authContext(), false, 0L));

		eventManager.call(new IdentityDetachedEvent(connectionUniqueId));

		assertFalse(service.isPending(connectionUniqueId));
	}

	@DisplayName("Other listeners of the required event already see the connection as pending")
	@Test
	void otherListenersSeeTheNewState() {
		AtomicBoolean seenPending = new AtomicBoolean();
		eventManager.register(new EventListener() {
			@IdenticEvent
			public void onRequired(@NotNull AuthenticationRequiredEvent event) {
				seenPending.set(service.isPending(event.getConnectionUniqueId()));
			}
		});

		eventManager.call(new AuthenticationRequiredEvent(connectionUniqueId, accountUniqueId, authContext(), false, 0L));

		assertTrue(seenPending.get());
	}

	private @NotNull AuthContext authContext() {
		return AuthContext.builder()
				.connectionUniqueId(connectionUniqueId)
				.identity(new ConnectionIdentity(accountUniqueId, "PlayerOne", "127.0.0.1"))
				.intendedServer("auth")
				.build();
	}
}
