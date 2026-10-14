package me.whereareiam.identica;

import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.migration.MigrationJourney;
import me.whereareiam.identica.model.migration.PendingMigration;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import me.whereareiam.identica.type.migration.MigrationResultStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Coordinates Identica's connection lifecycle.
 * <p>
 * This service is the product-level entry point for platform adapters and
 * commands. It delegates preparation, scenario progression, and post-connection
 * completion to the internal pipelines.
 */
public interface ConnectionCoordinator {
	/**
	 * Prepares a connection before scenario processing.
	 *
	 * @param request preparation request, or {@code null} when no preparation context is available
	 * @return preparation decision
	 */
	@NotNull CompletionStage<PrepareDecision> prepare(@Nullable PrepareRequest request);

	/**
	 * Processes a new connection scenario.
	 *
	 * @param request connection request, or {@code null} when no connection context is available
	 * @return connection decision
	 */
	@NotNull CompletionStage<ConnectionDecision> process(@Nullable ConnectionRequest request);

	/**
	 * Resumes a pending connection scenario.
	 *
	 * @param request resume request
	 * @return connection decision
	 */
	@NotNull CompletionStage<ConnectionDecision> resume(@NotNull ResumeRequest request);

	/**
	 * Advances a pending connection scenario after user input.
	 *
	 * @param request advance request
	 * @return connection decision
	 */
	@NotNull CompletionStage<ConnectionDecision> advance(@NotNull AdvanceRequest request);

	/**
	 * Starts a migration journey for a connection in the connection's turn, after every earlier run of the
	 * connection has finished. The journey waits for the player to connect again; starting it fires
	 * {@link me.whereareiam.identica.event.scenario.migration.MigrationRequiredEvent}.
	 *
	 * @param journey journey to start
	 * @return {@link MigrationResultStatus#STARTED STARTED} when the journey was started,
	 * {@link MigrationResultStatus#PENDING_EXISTS PENDING_EXISTS} when the connection already has a migration journey,
	 * or {@link MigrationResultStatus#FAILED FAILED} when the journey cannot be kept or another proxy changed the
	 * connection's pipeline state while the journey was being stored
	 */
	@NotNull CompletionStage<MigrationResultStatus> startMigrationJourney(@NotNull MigrationJourney journey);

	/**
	 * Cancels the migration journey of a connection in the connection's turn, after every earlier run of the
	 * connection has finished. Cancelling fires
	 * {@link me.whereareiam.identica.event.scenario.migration.MigrationResolvedEvent} with
	 * {@link me.whereareiam.identica.type.ScenarioResolution#CANCELLED CANCELLED}.
	 *
	 * @param connectionUniqueId connection whose journey is cancelled
	 * @return the cancelled journey, or empty when the connection had none or another proxy changed the connection's
	 * pipeline state while the journey was being cleared
	 */
	@NotNull CompletionStage<Optional<PendingMigration>> cancelMigrationJourney(@NotNull UUID connectionUniqueId);

	/**
	 * Finds the migration journey of a connection in the connection's turn, after every earlier run of the
	 * connection has finished, so a journey a run is still working on is found as well.
	 *
	 * @param connectionUniqueId connection whose journey is looked for
	 * @return the journey in {@link PendingMigration.Phase#STARTED STARTED} phase, or empty when the connection has none
	 */
	@NotNull CompletionStage<Optional<PendingMigration>> findMigrationJourney(@NotNull UUID connectionUniqueId);

	/**
	 * Runs pending post-connection completion for an attached identity.
	 *
	 * @param identity attached identity
	 */
	void complete(@NotNull Identity identity);
}
