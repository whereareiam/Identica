package me.whereareiam.identica.engine.pipeline;

import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.common.event.EventController;
import me.whereareiam.identica.connection.ConnectionLifecycleService;
import me.whereareiam.identica.engine.pipeline.scenario.PendingPipelineKickCoordinator;
import me.whereareiam.identica.event.scenario.authentication.AuthenticationRequiredEvent;
import me.whereareiam.identica.event.scenario.authentication.AuthenticationResolvedEvent;
import me.whereareiam.identica.event.scenario.migration.MigrationRequiredEvent;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.scheduler.*;
import me.whereareiam.identica.service.Scheduler;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.keystone.model.SerializerContent;
import me.whereareiam.keystone.model.SerializerOptions;
import me.whereareiam.keystone.serializer.SerializerEngine;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Pending Pipeline Kick Coordinator")
class PendingPipelineKickCoordinatorTest {
	@BeforeAll
	static void initializeSerializer() {
		Serializer.initialize(() -> TEST_SERIALIZER);
	}

	private static final SerializerEngine TEST_SERIALIZER = new SerializerEngine() {
		@Override
		public @NotNull String serialize(Component component) {
			return component.toString();
		}

		@Override
		public @NotNull Component serialize(SerializerContent content) {
			return Component.text(content.getMessage());
		}

		public @NotNull String renderTemplate(@NotNull SerializerContent content) {
			return content.getMessage();
		}

		@Override
		public @NotNull SerializerOptions.PlaceholderFormat getPlaceholderFormat() {
			return SerializerOptions.PlaceholderFormat.CURLY_BRACES;
		}
	};

	@DisplayName("Resolving a pending scenario cancels timeout tasks by stable flow reference")
	@Test
	void resolvedScenarioCancelsPendingKickByStableFlowReference() {
		EventController eventManager = new EventController();
		TestScheduler scheduler = new TestScheduler();
		IdentityService identityService = mock(IdentityService.class);
		ConnectionLifecycleService connectionLifecycleService = mock(ConnectionLifecycleService.class);
		TestIdentity identity = new TestIdentity(UUID.randomUUID(), "PlayerOne");
		when(identityService.findByConnectionUniqueId(identity.getConnectionUniqueId())).thenReturn(Optional.of(identity));

		new PendingPipelineKickCoordinator(
				this::messages,
				identityService,
				scheduler,
				eventManager,
				connectionLifecycleService
		);

		UUID connectionUniqueId = identity.getConnectionUniqueId();
		UUID accountUniqueId = UUID.randomUUID();
		AuthContext context = AuthContext.builder()
				.connectionUniqueId(connectionUniqueId)
				.identity(new ConnectionIdentity(accountUniqueId, "PlayerOne", "127.0.0.1"))
				.intendedServer("auth")
				.build();
		long expiresAt = System.currentTimeMillis() + 60_000L;

		eventManager.call(new AuthenticationRequiredEvent(
				connectionUniqueId,
				accountUniqueId,
				context,
				false,
				expiresAt,
				JourneyMode.SEAMLESS
		));
		assertTrue(scheduler.hasDelayedTasks());

		eventManager.call(new AuthenticationResolvedEvent(
				connectionUniqueId,
				accountUniqueId,
				context,
				ScenarioResolution.COMPLETED,
				true
		));

		assertFalse(scheduler.hasDelayedTasks());
	}

	@DisplayName("Expiring a pending migration terminates the connection and disconnects the identity")
	@Test
	void expiredMigrationTerminatesAndDisconnects() {
		EventController eventManager = new EventController();
		TestScheduler scheduler = new TestScheduler();
		IdentityService identityService = mock(IdentityService.class);
		ConnectionLifecycleService connectionLifecycleService = mock(ConnectionLifecycleService.class);
		UUID connectionUniqueId = UUID.randomUUID();
		UUID accountUniqueId = UUID.randomUUID();
		TestIdentity identity = new TestIdentity(connectionUniqueId, "PlayerOne");
		identity.setAccountUniqueId(accountUniqueId);
		when(identityService.findByConnectionUniqueId(connectionUniqueId)).thenReturn(Optional.of(identity));

		new PendingPipelineKickCoordinator(
				this::messages,
				identityService,
				scheduler,
				eventManager,
				connectionLifecycleService
		);

		MigrationContext context = MigrationContext.builder()
				.connectionUniqueId(connectionUniqueId)
				.identity(new ConnectionIdentity(accountUniqueId, "PlayerOne", "127.0.0.1"))
				.targetProviderId("premium")
				.build();
		eventManager.call(new MigrationRequiredEvent(
				connectionUniqueId,
				accountUniqueId,
				context,
				false,
				System.currentTimeMillis(),
				JourneyMode.SEAMLESS
		));

		scheduler.runAll();

		verify(connectionLifecycleService).terminated(connectionUniqueId, accountUniqueId, me.whereareiam.identica.type.pipeline.PipelineType.MIGRATION);
		assertEquals(Component.text("expired"), identity.lastDisconnect);
	}

	private Messages messages() {
		Messages messages = new Messages();
		Messages.Scenarios connection = new Messages.Scenarios();
		Messages.Scenarios.Authentication authentication = new Messages.Scenarios.Authentication();
		authentication.setPipelineExpired(List.of("expired"));
		Messages.Scenarios.Registration registration = new Messages.Scenarios.Registration();
		registration.setPipelineExpired(List.of("expired"));
		Messages.Scenarios.Migration migration = new Messages.Scenarios.Migration();
		migration.setPipelineExpired(List.of("expired"));
		migration.setCancelled(List.of("cancelled"));
		connection.setAuthentication(authentication);
		connection.setRegistration(registration);
		connection.setMigration(migration);
		messages.setScenarios(connection);
		return messages;
	}

	private static final class TestScheduler implements Scheduler {
		private final Map<JobKey, DelayedRunnableTask> delayedTasks = new HashMap<>();

		@Override
		public void schedule(RunnableTask runnableTask) {
		}

		@Override
		public void schedule(DelayedRunnableTask runnableTask) {
			delayedTasks.put(runnableTask.getKey(), runnableTask);
		}

		@Override
		public void schedule(PeriodicalRunnableTask runnableTask) {
		}

		@Override
		public void schedule(RunnableTask runnableTask, boolean async) {
			schedule(runnableTask);
		}

		@Override
		public void schedule(DelayedRunnableTask runnableTask, boolean async) {
			schedule(runnableTask);
		}

		@Override
		public void schedule(PeriodicalRunnableTask runnableTask, boolean async) {
			schedule(runnableTask);
		}

		@Override
		public void cancel(JobKey key) {
			delayedTasks.remove(key);
		}

		@Override
		public void cancelByOrigin(Origin origin) {
			delayedTasks.entrySet().removeIf(entry -> entry.getKey().getOrigin().equals(origin));
		}

		private boolean hasDelayedTasks() {
			return !delayedTasks.isEmpty();
		}

		private void runAll() {
			List<DelayedRunnableTask> tasks = new ArrayList<>(delayedTasks.values());
			delayedTasks.clear();
			for (DelayedRunnableTask task : tasks)
				task.getRunnable().run();
		}
	}

	private static final class TestIdentity extends Identity {
		private Component lastDisconnect;

		private TestIdentity(UUID uniqueId, String username) {
			super(uniqueId, username);
		}

		@Override
		public void sendMessage(@NonNull Component message) {
		}

		@Override
		public void sendTitle(@NonNull Title title) {
		}

		@Override
		public boolean hasPermission(@NonNull String permission) {
			return true;
		}

		@Override
		public @NonNull Locale getLocale() {
			return Locale.ENGLISH;
		}

		@Override
		public @NonNull Audience getAudience() {
			return Audience.empty();
		}

		@Override
		public void disconnect(@NonNull Component reason) {
			lastDisconnect = reason;
		}
	}
}
