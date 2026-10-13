package me.whereareiam.identica.engine.step;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.identity.username.UsernameAssigner;
import me.whereareiam.identica.model.identity.username.UsernameAssignment;
import me.whereareiam.identica.model.identity.username.UsernameArrival;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.pipeline.journey.step.type.SeamlessStep;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Refuses a registration the username conflict rules refuse, before the player is asked for anything. A player who
 * picked the provider on this connection only gets here once the provider is known.
 */
@Singleton
public class UsernameAvailabilityStep extends SeamlessStep {
	private static final int ORDER = -100;

	private final UsernameAssigner assigner;

	@Inject
	public UsernameAvailabilityStep(UsernameAssigner assigner) {
		super("username-availability");
		this.assigner = assigner;
	}

	@Override
	public int order() {
		return ORDER;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		String username = context.getUsername();
		ProviderContext provider = context.getProvider();
		if (username == null || username.isBlank() || provider == null || provider.getProviderId() == null)
			return CompletableFuture.completedFuture(StepResult.proceed(context));

		UsernameAssignment assignment = assigner.preview(UsernameArrival.builder()
				.accountUniqueId(context.getAccountUniqueId())
				.providerId(provider.getProviderId())
				.username(username)
				.firstJoin(true)
				.arrivedVia(context.getSelection().getOrigin())
				.build());
		if (!assignment.isRefused()) return CompletableFuture.completedFuture(StepResult.proceed(context));

		return CompletableFuture.completedFuture(StepResult.denied(assignment.getMessage()));
	}
}
