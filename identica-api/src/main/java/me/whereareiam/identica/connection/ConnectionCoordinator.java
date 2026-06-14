package me.whereareiam.identica.connection;

import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.auth.request.ConnectionRequest;
import me.whereareiam.identica.model.auth.request.ResumeRequest;
import me.whereareiam.identica.model.pipeline.prepare.PrepareRequest;
import me.whereareiam.identica.model.pipeline.prepare.decision.PrepareDecision;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
	 * Checks whether a connection has a pending scenario.
	 *
	 * @param connectionUniqueId connection unique id
	 * @return {@code true} when a pending scenario exists
	 */
	boolean hasPending(@NotNull UUID connectionUniqueId);

	/**
	 * Runs pending post-connection completion for an attached identity.
	 *
	 * @param identity attached identity
	 */
	void complete(@NotNull Identity identity);
}
