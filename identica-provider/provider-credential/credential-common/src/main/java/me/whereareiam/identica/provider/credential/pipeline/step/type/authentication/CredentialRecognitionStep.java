package me.whereareiam.identica.provider.credential.pipeline.step.type.authentication;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.recognition.SessionRecognitionService;
import me.whereareiam.identica.feature.recognition.store.RecognizedConnectionStore;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.provider.credential.CredentialConstants;
import me.whereareiam.identica.provider.credential.pipeline.step.base.AbstractCredentialStep;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Singleton
public class CredentialRecognitionStep extends AbstractCredentialStep {
	private final @NotNull FeatureRegistry features;
	private final @NotNull Injector injector;

	@Inject
	public CredentialRecognitionStep(
			@NotNull FeatureRegistry features,
			@NotNull Injector injector
	) {
		super("password-recognition");
		this.features = features;
		this.injector = injector;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		if (!features.isEnabled("credential", "recognition"))
			return CompletableFuture.completedFuture(StepResult.proceed(context));

		String providerSubject = requireProviderSubject(context);
		boolean recognized = injector.getInstance(SessionRecognitionService.class).matches(
				CredentialConstants.PROVIDER_ID,
				providerSubject,
				context.getUsername(),
				context.getIp(),
				context.getIdentity().getOrigin()
		);
		if (recognized && context.getIdentity().getConnectionUniqueId() != null)
			injector.getInstance(RecognizedConnectionStore.class).markRecognized(context.getIdentity().getConnectionUniqueId());

		return CompletableFuture.completedFuture(recognized
				? StepResult.complete(context)
				: StepResult.proceed(context));
	}
}
