package me.whereareiam.identica.provider.premium.pipeline.step.type.migration;

import com.google.inject.Singleton;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.AbstractStep;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Singleton
public class PremiumMigrationCompleteStep extends AbstractStep {
	public PremiumMigrationCompleteStep() {
		super("premium-migration-complete");
	}

	@Override
	public int order() {
		return 40;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		return CompletableFuture.completedFuture(StepResult.complete(context));
	}
}
