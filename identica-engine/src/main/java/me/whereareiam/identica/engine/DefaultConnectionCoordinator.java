package me.whereareiam.identica.engine;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.connection.ConnectionCoordinator;
import me.whereareiam.identica.engine.pipeline.completion.runtime.CompletionPipeline;
import me.whereareiam.identica.engine.pipeline.prepare.runtime.PreparePipeline;
import me.whereareiam.identica.engine.pipeline.scenario.ScenarioPipeline;
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

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class DefaultConnectionCoordinator implements ConnectionCoordinator {
	private final PreparePipeline preparePipeline;
	private final ScenarioPipeline scenarioPipeline;
	private final CompletionPipeline completionPipeline;

	@Override
	public @NotNull CompletionStage<PrepareDecision> prepare(@Nullable PrepareRequest request) {
		return preparePipeline.prepare(request)
				.thenApply(decision -> decision != null
						? decision
						: PrepareDecision.allow());
	}

	@Override
	public @NotNull CompletionStage<ConnectionDecision> process(@Nullable ConnectionRequest request) {
		return scenarioPipeline.process(request);
	}

	@Override
	public @NotNull CompletionStage<ConnectionDecision> resume(@NotNull ResumeRequest request) {
		return scenarioPipeline.resume(request);
	}

	@Override
	public @NotNull CompletionStage<ConnectionDecision> advance(@NotNull AdvanceRequest request) {
		return scenarioPipeline.advance(request);
	}

	@Override
	public boolean hasPending(@NotNull UUID connectionUniqueId) {
		return scenarioPipeline.hasPending(connectionUniqueId);
	}

	@Override
	public void complete(@NotNull Identity identity) {
		completionPipeline.complete(identity);
	}
}
