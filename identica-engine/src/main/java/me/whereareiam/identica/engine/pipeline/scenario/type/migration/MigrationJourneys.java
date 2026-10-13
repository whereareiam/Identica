package me.whereareiam.identica.engine.pipeline.scenario.type.migration;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.engine.pipeline.scenario.ConnectionRuns;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.event.scenario.migration.MigrationRequiredEvent;
import me.whereareiam.identica.event.scenario.migration.MigrationResolvedEvent;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.migration.MigrationContext;
import me.whereareiam.identica.model.migration.MigrationJourney;
import me.whereareiam.identica.model.migration.PendingMigration;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.pipeline.migration.MigrationPendingState;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.type.ScenarioResolution;
import me.whereareiam.identica.type.migration.MigrationResultStatus;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Starts, cancels and finds the migration journeys commands and administrators ask for. Each of them works on the
 * connection's pipeline state in the connection's turn, so it neither misses a journey a run has taken out of the
 * store nor has its change overwritten when that run saves.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class MigrationJourneys {
	private final ConnectionRuns connectionRuns;
	private final PipelineStateStore pipelineStateStore;
	private final EventManager eventManager;
	private final Provider<Engine> engineProvider;

	public @NotNull CompletionStage<MigrationResultStatus> start(@NotNull MigrationJourney journey) {
		return connectionRuns.submit(journey.getConnectionUniqueId(), () -> CompletableFuture.completedFuture(store(journey)));
	}

	public @NotNull CompletionStage<Optional<PendingMigration>> cancel(@NotNull UUID connectionUniqueId) {
		return connectionRuns.submit(connectionUniqueId, () -> CompletableFuture.completedFuture(clear(connectionUniqueId)));
	}

	public @NotNull CompletionStage<Optional<PendingMigration>> find(@NotNull UUID connectionUniqueId) {
		return connectionRuns.submit(connectionUniqueId, () -> CompletableFuture.completedFuture(
				Optional.ofNullable(started(connectionUniqueId, stored(connectionUniqueId)))
		));
	}

	private @NotNull MigrationResultStatus store(@NotNull MigrationJourney journey) {
		if (started(journey.getConnectionUniqueId(), stored(journey.getConnectionUniqueId())) != null)
			return MigrationResultStatus.PENDING_EXISTS;

		Engine.Scenario scenario = engineProvider.get().getScenarios().getMigration();
		long ttlMs = scenario.pipelineTtlMillis();
		if (ttlMs <= 0) return MigrationResultStatus.FAILED;

		JourneyMode journeyMode = scenario.getJourneyMode();
		MigrationContext context = MigrationContext.builder()
				.connectionUniqueId(journey.getConnectionUniqueId())
				.identity(new ConnectionIdentity(
						journey.getAccountUniqueId(),
						journey.getUsername() != null ? journey.getUsername() : "",
						journey.getIp()
				))
				.targetProviderId(journey.getTargetProviderId())
				.build();

		PipelineState state = PipelineState.initial();
		state.setPipelineType(PipelineType.MIGRATION);
		state.setScenario(context);
		state.put(MigrationPendingState.KEY, new MigrationPendingState(
				journey.getTargetProviderId(),
				journey.getRequestedAt(),
				journey.getInitiator(),
				journey.getInitiatorUniqueId()
		), ttlMs);
		state.put(JourneyStateItem.KEY, new JourneyStateItem(journeyMode, null, 0), ttlMs);

		pipelineStateStore.save(PipelineStateReference.from(context), state, ttlMs);
		eventManager.call(new MigrationRequiredEvent(context, false, System.currentTimeMillis() + ttlMs, journeyMode));
		Logger.debug(
				"Stored migration pending connection=%s identica=%s target=%s username=%s ip=%s journeyMode=%s",
				journey.getConnectionUniqueId(),
				journey.getAccountUniqueId(),
				journey.getTargetProviderId(),
				journey.getUsername(),
				journey.getIp(),
				journeyMode
		);

		return MigrationResultStatus.STARTED;
	}

	private @NotNull Optional<PendingMigration> clear(@NotNull UUID connectionUniqueId) {
		PipelineState state = stored(connectionUniqueId);
		PendingMigration started = started(connectionUniqueId, state);
		if (started == null) return Optional.empty();

		MigrationContext context = (MigrationContext) state.getScenario(PipelineType.MIGRATION);
		if (context != null && context.getConnectionUniqueId() != null)
			eventManager.call(new MigrationResolvedEvent(context, ScenarioResolution.CANCELLED, false));
		pipelineStateStore.clear(reference(connectionUniqueId));

		return Optional.of(started);
	}

	private @Nullable PipelineState stored(@NotNull UUID connectionUniqueId) {
		return pipelineStateStore.find(reference(connectionUniqueId)).orElse(null);
	}

	/**
	 * Reads the started migration journey out of a connection's state.
	 *
	 * @return the journey, or {@code null} when the state holds none
	 */
	private @Nullable PendingMigration started(@NotNull UUID connectionUniqueId, @Nullable PipelineState state) {
		if (state == null) {
			Logger.debug("Migration pending lookup missed connection=%s", connectionUniqueId);
			return null;
		}

		MigrationPendingState pending = state.get(MigrationPendingState.KEY).orElse(null);
		if (pending == null) {
			Logger.debug(
					"Migration pending lookup found state without pending marker connection=%s pipeline=%s",
					connectionUniqueId,
					state.getPipelineType()
			);
			return null;
		}

		MigrationContext context = (MigrationContext) state.getScenario(PipelineType.MIGRATION);
		PendingMigration.PendingMigrationBuilder started = PendingMigration.builder()
				.connectionUniqueId(connectionUniqueId)
				.targetProviderId(pending.getTargetProviderId())
				.requestedAt(pending.getRequestedAt())
				.initiator(pending.getInitiator())
				.initiatorUniqueId(pending.getInitiatorUniqueId())
				.phase(PendingMigration.Phase.STARTED);
		if (context != null) {
			if (context.getConnectionUniqueId() != null) started.connectionUniqueId(context.getConnectionUniqueId());
			started.targetProviderId(context.getTargetProviderId());
			started.uniqueId(context.getIdentity().getAccountUniqueId());
		}

		return started.build();
	}

	private static @NotNull PipelineStateReference reference(@NotNull UUID connectionUniqueId) {
		return PipelineStateReference.builder()
				.connectionUniqueId(connectionUniqueId)
				.build();
	}
}
