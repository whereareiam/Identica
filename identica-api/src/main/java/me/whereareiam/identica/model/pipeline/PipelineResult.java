package me.whereareiam.identica.model.pipeline;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.type.pipeline.PipelineStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@RequiredArgsConstructor
@SuppressWarnings("unused")
public final class PipelineResult {
	private final @Nullable PipelineState state;
	private final @NotNull PipelineStatus status;
	/**
	 * What the player is told, or {@code null} for nothing.
	 */
	private final @Nullable PlayerMessage message;

	public static @NotNull PipelineResult continueWith() {
		return new PipelineResult(null, PipelineStatus.CONTINUE, null);
	}

	public static @NotNull PipelineResult waiting(@Nullable PlayerMessage message) {
		return new PipelineResult(null, PipelineStatus.WAITING, message);
	}

	public static @NotNull PipelineResult complete() {
		return new PipelineResult(null, PipelineStatus.COMPLETE, null);
	}

	public static @NotNull PipelineResult failed(@Nullable PlayerMessage message) {
		return new PipelineResult(null, PipelineStatus.FAILED, message);
	}

	public static @NotNull PipelineResult denied(@Nullable PlayerMessage message) {
		return new PipelineResult(null, PipelineStatus.DENIED, message);
	}

	public static @NotNull PipelineResult requireReconnect(@Nullable PlayerMessage message) {
		return new PipelineResult(null, PipelineStatus.REQUIRE_RECONNECT, message);
	}

	public static @NotNull PipelineResult noPending() {
		return new PipelineResult(null, PipelineStatus.NO_PENDING, null);
	}

	public static @NotNull PipelineResult fromStepResult(@NotNull StepResult result) {
		PipelineStatus status = switch (result.getStatus()) {
			case CONTINUE -> PipelineStatus.CONTINUE;
			case WAITING -> PipelineStatus.WAITING;
			case COMPLETE -> PipelineStatus.COMPLETE;
			case FAILED -> PipelineStatus.FAILED;
			case DENIED -> PipelineStatus.DENIED;
			case REQUIRE_RECONNECT -> PipelineStatus.REQUIRE_RECONNECT;
			case NO_PENDING -> PipelineStatus.NO_PENDING;
		};
		return new PipelineResult(null, status, result.getMessage());
	}

	public @NotNull PipelineResult withState(@Nullable PipelineState state) {
		return new PipelineResult(state, status, message);
	}
}
