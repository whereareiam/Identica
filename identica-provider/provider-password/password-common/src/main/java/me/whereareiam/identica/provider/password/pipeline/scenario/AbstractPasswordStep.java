package me.whereareiam.identica.provider.password.pipeline.scenario;

import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.type.InteractiveStep;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public abstract class AbstractPasswordStep extends InteractiveStep {
	protected AbstractPasswordStep(@NotNull String name) {
		super(name);
	}

	protected @NotNull ProviderContext requireProvider(@NotNull ScenarioContext context) {
		return Objects.requireNonNull(
				context.getProvider(),
				"Password scenario steps require provider context after validation"
		);
	}

	protected @NotNull String requireProviderSubject(@NotNull ScenarioContext context) {
		String providerSubject = Objects.requireNonNull(
				requireProvider(context).getProviderSubject(),
				"Password scenario steps require provider subject after validation"
		);

		if (providerSubject.isBlank()) throw new IllegalStateException("Password scenario steps require non-blank provider subject");
		return providerSubject;
	}
}
