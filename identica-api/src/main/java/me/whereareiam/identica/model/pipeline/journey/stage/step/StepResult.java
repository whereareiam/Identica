package me.whereareiam.identica.model.pipeline.journey.stage.step;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.type.pipeline.journey.step.StepWaitReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
@ToString
@RequiredArgsConstructor
@SuppressWarnings("unused")
public class StepResult {
	private final @NotNull StepStatus status;
	private final @Nullable String message;
	private final @Nullable ScenarioContext updatedContext;
	private final @Nullable StepWaitReason waitReason;
	/**
	 * Name of an earlier step of the same stage where the journey continues once the player answers, or
	 * {@code null} to continue at the waiting step.
	 */
	private final @Nullable String resumeStep;

	public static @NotNull StepResult proceed(@Nullable ScenarioContext context) {
		return new StepResult(StepStatus.CONTINUE, null, context, null, null);
	}

	public static @NotNull StepResult waiting(@Nullable String message) {
		return waiting(message, StepWaitReason.INPUT);
	}

	public static @NotNull StepResult waiting(@Nullable String message, @NotNull StepWaitReason waitReason) {
		return new StepResult(StepStatus.WAITING, message, null, waitReason, null);
	}

	/**
	 * Waits for the player's input, and continues the journey at an earlier step of the same stage once it arrives
	 * instead of at the waiting step, such as a registration that asks for a new password after its repetition did
	 * not match. A step the stage does not run before the waiting one leaves the journey at the waiting step.
	 *
	 * @param step the name of the step where the journey continues
	 * @param message the message to the player
	 * @return the waiting result
	 */
	public static @NotNull StepResult waitingAt(@NotNull String step, @Nullable String message) {
		return new StepResult(StepStatus.WAITING, message, null, StepWaitReason.INPUT, step);
	}

	public static @NotNull StepResult complete(@Nullable ScenarioContext context) {
		return new StepResult(StepStatus.COMPLETE, null, context, null, null);
	}

	public static @NotNull StepResult failed(@Nullable String message) {
		return new StepResult(StepStatus.FAILED, message, null, null, null);
	}

	public static @NotNull StepResult denied(@Nullable String message) {
		return new StepResult(StepStatus.DENIED, message, null, null, null);
	}

	public static @NotNull StepResult requireReconnect(@Nullable String message) {
		return new StepResult(StepStatus.REQUIRE_RECONNECT, message, null, null, null);
	}

	public static @NotNull StepResult noPending() {
		return new StepResult(StepStatus.NO_PENDING, null, null, null, null);
	}

	public enum StepStatus {
		CONTINUE,
		WAITING,
		COMPLETE,
		FAILED,
		DENIED,
		REQUIRE_RECONNECT,
		NO_PENDING
	}
}
