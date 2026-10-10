package me.whereareiam.identica.provider.premium.pipeline.step.shared;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.provider.premium.pipeline.step.base.AbstractProfileVerificationStep;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileStore;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

@Singleton
public class ProfilePresenceStep extends AbstractProfileVerificationStep {
	@Inject
	public ProfilePresenceStep(
			Provider<PremiumMessages> messagesProvider,
			PremiumProfileStore profileStore,
			HandshakeStore handshakeStore,
			Provider<Engine> engineProvider
	) {
		super("profile-presence", messagesProvider, profileStore, handshakeStore, engineProvider);
	}

	@Override
	public int order() {
		return 10;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
        String username = context.getUsername();
		String ip = context.getIp();
		if (username == null || username.isBlank() || ip == null || ip.isBlank())
			return CompletableFuture.completedFuture(failed());

		String providerSubject = readProfileId(username);
		if (providerSubject == null || providerSubject.isBlank()) {
			Logger.debug("Premium profile verification missing snapshot username=%s ip=%s", username, ip);
			return CompletableFuture.completedFuture(failed());
		}
		Logger.debug("Premium profile verification found snapshot username=%s ip=%s subject=%s", username, ip, providerSubject);

		return CompletableFuture.completedFuture(StepResult.proceed(context));
	}
}
