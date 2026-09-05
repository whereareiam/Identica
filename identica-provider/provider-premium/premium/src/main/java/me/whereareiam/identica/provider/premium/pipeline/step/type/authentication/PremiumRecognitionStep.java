package me.whereareiam.identica.provider.premium.pipeline.step.type.authentication;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.recognition.SessionRecognitionService;
import me.whereareiam.identica.feature.recognition.store.RecognizedConnectionStore;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.type.InteractiveStep;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Singleton
public class PremiumRecognitionStep extends InteractiveStep {
	private final @NotNull FeatureRegistry features;
	private final @NotNull Injector injector;

	@Inject
	public PremiumRecognitionStep(
			@NotNull FeatureRegistry features,
			@NotNull Injector injector
	) {
		super("premium-recognition");
		this.features = features;
		this.injector = injector;
	}

	@Override
	public int order() {
		return 35;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		if (!features.isEnabled("premium", "recognition"))
			return CompletableFuture.completedFuture(StepResult.proceed(context));

		ProviderContext provider = context.getProvider();
		if (provider == null)
			return CompletableFuture.completedFuture(StepResult.proceed(context));

		boolean recognized = injector.getInstance(SessionRecognitionService.class).matches(
				provider.getProviderId(),
				provider.getProviderSubject(),
				provider.getProviderUsername(),
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
