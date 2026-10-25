package me.whereareiam.identica.example.provider.pipeline.step;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.example.provider.ExampleMessageDefinitions;
import me.whereareiam.identica.example.provider.config.ExampleMessages;
import me.whereareiam.identica.example.provider.config.ExampleSettings;
import me.whereareiam.identica.example.provider.pipeline.ExamplePassphraseAttempt;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.StatefulStep;
import me.whereareiam.identica.pipeline.journey.step.type.InteractiveStep;
import me.whereareiam.identica.pipeline.state.PipelineState;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Waits until the player has typed the passphrase. Identica runs the step when the player joins, and again each
 * time {@code /passphrase} hands it an attempt; completing it completes this provider's part of the journey.
 */
@Singleton
public class ExamplePassphraseStep extends InteractiveStep implements StatefulStep {
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
		if (attempt == null)
			return CompletableFuture.completedFuture(StepResult.waiting(ExampleMessageDefinitions.PASSPHRASE.create(messages.get())));

		state.remove(ExamplePassphraseAttempt.KEY);
		if (!settings.get().getPassphrase().equals(attempt.getPassphrase()))
			return CompletableFuture.completedFuture(StepResult.waiting(ExampleMessageDefinitions.PASSPHRASE_INVALID.create(messages.get())));

		return CompletableFuture.completedFuture(StepResult.complete(context));
	}
}
