package me.whereareiam.identica.engine.pipeline.scenario.shared.group.session.phase.base;

import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.pipeline.PipelineResult;
import me.whereareiam.identica.model.pipeline.phase.PhaseResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.PipelinePhase;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.state.AbstractGroupState;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

@RequiredArgsConstructor
public abstract class AbstractBuildSessionPhase<C extends ScenarioContext, S extends AbstractGroupState> implements PipelinePhase<S> {
	private final Provider<Messages> messagesProvider;

	@Override
	public @NotNull String id() {
		return "build-session";
	}

	@Override
	public int order() {
		return 100;
	}

	@Override
	public @NotNull CompletionStage<PhaseResult<S>> execute(
			@NotNull PipelineState pipelineState,
			@NotNull S state
	) {
		PipelineResult result = state.getResult();
		if (result == null || result.getStatus() != PipelineStatus.COMPLETE)
			return CompletableFuture.completedFuture(PhaseResult.pass(state));

		C context = resolveContext(state, pipelineState);
		if (context == null) {
			state.setResult(PipelineResult.failed(sessionBuildMissingMessage(messagesProvider.get())));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		ProviderContext provider = context.getProvider();
		String currentUsername = context.getUsername();
		if (provider == null || isBlank(currentUsername) || context.getAccountUniqueId() == null) {
			state.setResult(PipelineResult.failed(sessionBuildMissingMessage(messagesProvider.get())));
			return CompletableFuture.completedFuture(PhaseResult.pass(state));
		}

		String providerUsername = provider.getProviderUsername();
		String originalUsername = isBlank(providerUsername)
				? currentUsername
				: providerUsername;

		Session session = Session.builder()
				.uniqueId(context.getAccountUniqueId())
				.providerId(provider.getProviderId())
				.providerSubject(provider.getProviderSubject())
				.originalUsername(originalUsername)
				.effectiveUsername(currentUsername)
				.connection(connection(context.getConnectionUniqueId()))
				.ip(context.getIp())
				.createdAt(System.currentTimeMillis())
				.build();

		storeContext(state, context);
		storeSession(state, session);
		return CompletableFuture.completedFuture(PhaseResult.pass(state));
	}

	protected abstract @Nullable C resolveContext(@NotNull S state, @NotNull PipelineState pipelineState);

	protected abstract void storeContext(@NotNull S state, @NotNull C context);

	protected abstract void storeSession(@NotNull S state, @NotNull Session session);

	protected abstract @NotNull String sessionBuildMissingMessage(@NotNull Messages messages);

	private @Nullable SessionConnection connection(@Nullable UUID connectionUniqueId) {
		return connectionUniqueId != null ? SessionConnection.of(connectionUniqueId) : null;
	}

	private boolean isBlank(@Nullable String value) {
		return value == null || value.isBlank();
	}
}
