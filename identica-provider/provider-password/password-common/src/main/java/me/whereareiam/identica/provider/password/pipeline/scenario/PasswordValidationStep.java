package me.whereareiam.identica.provider.password.pipeline.scenario;

import com.google.inject.Singleton;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.provider.password.PasswordConstants;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Singleton
public class PasswordValidationStep extends AbstractPasswordStep {
	public PasswordValidationStep() {
		super("password-validation");
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		ProviderContext provider = context.getProvider();
		if (provider == null) {
			Logger.debug("Password validation missing provider context connection=%s username=%s ip=%s",
					context.getConnectionUniqueId(),
					context.getUsername(),
					context.getIp());
			return CompletableFuture.completedFuture(StepResult.failed(""));
		}
		if (!PasswordConstants.PROVIDER_ID.equalsIgnoreCase(provider.getProviderId())) {
			Logger.debug("Password validation provider mismatch connection=%s expected=%s actual=%s subject=%s",
					context.getConnectionUniqueId(),
					PasswordConstants.PROVIDER_ID,
					provider.getProviderId(),
					provider.getProviderSubject());
			return CompletableFuture.completedFuture(StepResult.failed(""));
		}

		String providerSubject = provider.getProviderSubject();
		if (providerSubject == null || providerSubject.isBlank()) {
			Logger.debug("Password validation missing provider subject connection=%s username=%s provider=%s",
					context.getConnectionUniqueId(),
					context.getUsername(),
					provider.getProviderId());
			return CompletableFuture.completedFuture(StepResult.failed(""));
		}
		Logger.debug("Password validation accepted connection=%s username=%s subject=%s",
				context.getConnectionUniqueId(),
				context.getUsername(),
				providerSubject);

		return CompletableFuture.completedFuture(StepResult.proceed(context));
	}
}
