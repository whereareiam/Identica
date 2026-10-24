package me.whereareiam.identica.example.provider.pipeline.step;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.example.provider.config.ExampleMessages;
import me.whereareiam.identica.example.provider.config.ExampleSettings;
import me.whereareiam.identica.example.provider.pipeline.ExamplePassphraseAttempt;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.StatefulStep;
import me.whereareiam.identica.pipeline.journey.step.AbstractStep;
import me.whereareiam.identica.pipeline.state.PipelineState;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Waits until the player has typed the passphrase. Identica runs the step when the player joins, and again each
 * time {@code /passphrase} hands it an attempt; completing it completes this provider's part of the journey.
 */
@Singleton
public class ExamplePassphraseStep extends AbstractStep implements StatefulStep {
	/**
	 * ID of what the step asks for: the passphrase.
	 */
	public static final String PROMPT = "example:passphrase";
	/**
	 * ID of why the step asks again: the passphrase was wrong.
	 */
	public static final String INVALID = "example:passphrase-invalid";

	private final Provider<ExampleSettings> settings;
	private final Provider<ExampleMessages> messages;

	@Inject
	public ExamplePassphraseStep(Provider<ExampleSettings> settings, Provider<ExampleMessages> messages) {
		super("example-passphrase");
		this.settings = settings;
		this.messages = messages;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context, @NotNull PipelineState state) {
		ExamplePassphraseAttempt attempt = state.get(ExamplePassphraseAttempt.KEY).orElse(null);
		if (attempt == null) return CompletableFuture.completedFuture(StepResult.waiting(messages.get().getPrompt()).withPrompt(PROMPT));

		state.remove(ExamplePassphraseAttempt.KEY);
		if (!settings.get().getPassphrase().equals(attempt.getPassphrase()))
			return CompletableFuture.completedFuture(StepResult.waiting(messages.get().getInvalid())
					.withPrompt(PROMPT)
					.withAnswer(INVALID));

		return CompletableFuture.completedFuture(StepResult.complete(context));
	}
}
