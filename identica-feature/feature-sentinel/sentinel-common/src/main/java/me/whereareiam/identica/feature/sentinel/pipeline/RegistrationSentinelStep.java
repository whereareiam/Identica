package me.whereareiam.identica.feature.sentinel.pipeline;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.feature.sentinel.SentinelService;
import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelDecision;
import me.whereareiam.identica.feature.sentinel.type.SentinelScope;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.AbstractStep;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Refuses a registration a sentinel of the {@link SentinelScope#REGISTRATION} scope limits, once the provider is
 * known and before the player is asked for anything.
 */
@Singleton
public class RegistrationSentinelStep extends AbstractStep {
	/**
	 * Right after the username check of every registration, ahead of the provider's own steps.
	 */
	private static final int ORDER = -90;

	private final SentinelService sentinels;

	@Inject
	public RegistrationSentinelStep(SentinelService sentinels) {
		super("sentinel-registration");
		this.sentinels = sentinels;
	}

	@Override
	public int order() {
		return ORDER;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		ProviderContext provider = context.getProvider();
		SentinelContext sentinelContext = SentinelContext.builder()
				.providerId(provider != null ? provider.getProviderId() : null)
				.uniqueId(context.getAccountUniqueId())
				.connectionUniqueId(context.getConnectionUniqueId())
				.username(context.getUsername())
				.ip(context.getIp())
				.build();

		SentinelDecision decision = sentinels.evaluate(SentinelScope.REGISTRATION, sentinelContext).orElse(null);
		if (decision == null || !decision.isLimited() || !decision.isDeny())
			return CompletableFuture.completedFuture(StepResult.proceed(context));

		return CompletableFuture.completedFuture(StepResult.denied(decision.getMessage()));
	}
}
