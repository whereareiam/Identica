package me.whereareiam.identica.model.pipeline.journey.stage.step;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import me.whereareiam.identica.pipeline.ScenarioContext;
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
	/**
	 * Name of an earlier step of the same stage where the journey continues once the player answers, or
	 * {@code null} to continue at the waiting step.
	 */
	private final @Nullable String resumeStep;
	/**
	 * ID of what a step waiting for input asks the player for, such as {@code credential:password}, or {@code null}
	 * when the step names none. Steps that ask for the same thing name it the same way, so a prompt can be shown other
	 * than in chat, such as in a dialog.
	 */
	private final @Nullable String prompt;
	/**
	 * ID of why a step waiting for input asks again, such as {@code credential:password-invalid}, or {@code null} on
	 * the first ask.
	 */
	private final @Nullable String answer;

	public static @NotNull StepResult proceed(@Nullable ScenarioContext context) {
		return new StepResult(StepStatus.CONTINUE, null, context, null, null, null);
	}

	/**
	 * Waits for the player's input. A step may wait while the player still logs in: the journey then sends the player
	 * to the step's server and runs the step again once the player is there, so the message reaches the player.
	 *
	 * @param message the message to the player
	 * @return the waiting result
	 */
	public static @NotNull StepResult waiting(@Nullable String message) {
		return new StepResult(StepStatus.WAITING, message, null, null, null, null);
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
		return new StepResult(StepStatus.WAITING, message, null, step, null, null);
	}

	public static @NotNull StepResult complete(@Nullable ScenarioContext context) {
		return new StepResult(StepStatus.COMPLETE, null, context, null, null, null);
	}

	public static @NotNull StepResult failed(@Nullable String message) {
		return new StepResult(StepStatus.FAILED, message, null, null, null, null);
	}

	public static @NotNull StepResult denied(@Nullable String message) {
		return new StepResult(StepStatus.DENIED, message, null, null, null, null);
	}

	public static @NotNull StepResult requireReconnect(@Nullable String message) {
		return new StepResult(StepStatus.REQUIRE_RECONNECT, message, null, null, null, null);
	}

	public static @NotNull StepResult noPending() {
		return new StepResult(StepStatus.NO_PENDING, null, null, null, null, null);
	}

	/**
	 * Returns this result with the ID of what the waiting step asks the player for. The chat message stays as it is.
	 *
	 * @param prompt the prompt's ID, {@code <owner>:<name>}, such as {@code credential:password}
	 * @return the result with the prompt
	 */
	public @NotNull StepResult withPrompt(@NotNull String prompt) {
		return new StepResult(status, message, updatedContext, resumeStep, prompt, answer);
	}

	/**
	 * Returns this result with the ID of why the waiting step asks again, such as a wrong password. The chat message
	 * stays as it is.
	 *
	 * @param answer the answer's ID, {@code <owner>:<name>}, such as {@code credential:password-invalid}
	 * @return the result with the answer
	 */
	public @NotNull StepResult withAnswer(@NotNull String answer) {
		return new StepResult(status, message, updatedContext, resumeStep, prompt, answer);
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
