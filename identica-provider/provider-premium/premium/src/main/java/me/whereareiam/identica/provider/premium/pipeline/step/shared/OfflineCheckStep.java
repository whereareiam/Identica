package me.whereareiam.identica.provider.premium.pipeline.step.shared;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.ScenarioContext;
import me.whereareiam.identica.provider.ProviderAttemptStore;
import me.whereareiam.identica.provider.premium.PremiumConstants;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.provider.premium.pipeline.step.base.AbstractProfileVerificationStep;
import me.whereareiam.identica.provider.premium.policy.PremiumHandshakeInstructions;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileStore;
import me.whereareiam.identica.util.UniqueIdGenerator;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Singleton
public class OfflineCheckStep extends AbstractProfileVerificationStep {
	private final ProviderAttemptStore attemptStore;
	private final PremiumHandshakeInstructions instructions;

	@Inject
	public OfflineCheckStep(
			Provider<PremiumMessages> messagesProvider,
			PremiumProfileStore profileStore,
			ProviderAttemptStore attemptStore,
			HandshakeStore handshakeStore,
			Provider<Engine> engineProvider,
			PremiumHandshakeInstructions instructions
	) {
		super("offline-check", messagesProvider, profileStore, handshakeStore, engineProvider);
		this.attemptStore = attemptStore;
		this.instructions = instructions;
	}

	@Override
	public int order() {
		return 20;
	}

	@Override
	public @NotNull CompletableFuture<StepResult> execute(@NotNull ScenarioContext context) {
		PremiumMessages.Verification verification = verification();
		String username = context.getUsername();
		String ip = context.getIp();
		if (username == null || username.isBlank() || ip == null || ip.isBlank())
			return CompletableFuture.completedFuture(failed());

		String providerSubject = readProfileId(username);
		if (providerSubject == null || providerSubject.isBlank()) {
			return CompletableFuture.completedFuture(failed());
		}

		UUID offlineUuid = UniqueIdGenerator.offlinePlayerUniqueId(username);
		if (offlineUuid != null && providerSubject.equalsIgnoreCase(offlineUuid.toString())) {
			if (!hasHandshakeAttempt(username, ip)) {
				markHandshakeAttempt(username, ip);
				Logger.debug(
						"Premium offline verification requires reconnect username=%s ip=%s subject=%s offline=%s",
						username,
						ip,
						providerSubject,
						offlineUuid
				);
				return CompletableFuture.completedFuture(requireReconnect(verification, username, ip));
			}

			clearHandshakeAttempt(username, ip);
			handshakeStore.invalidateInstruction(username, ip);
			Logger.debug(
					"Premium offline verification rejected offline session username=%s ip=%s subject=%s offline=%s",
					username,
					ip,
					providerSubject,
					offlineUuid
			);
			return CompletableFuture.completedFuture(StepResult.failed(""));
		}
		Logger.debug(
				"Premium offline verification accepted online session username=%s ip=%s subject=%s offline=%s",
				username,
				ip,
				providerSubject,
				offlineUuid
		);

		return CompletableFuture.completedFuture(StepResult.proceed(context));
	}

	private StepResult requireReconnect(
			PremiumMessages.Verification verification,
			String username,
			String ip
	) {
		instructions.forceOnline(username, ip);

		return StepResult.requireReconnect(joinLines(verification.getRejoin()));
	}

	private boolean hasHandshakeAttempt(@NotNull String username, @NotNull String ip) {
		return attemptStore.hasAttempt(PremiumConstants.PROVIDER_ID, PremiumConstants.ATTEMPT_SCOPE_VERIFY, username, ip);
	}

	private void markHandshakeAttempt(@NotNull String username, @NotNull String ip) {
		attemptStore.markAttempt(PremiumConstants.PROVIDER_ID, PremiumConstants.ATTEMPT_SCOPE_VERIFY, username, ip);
	}

	private void clearHandshakeAttempt(@NotNull String username, @NotNull String ip) {
		attemptStore.clearAttempt(PremiumConstants.PROVIDER_ID, PremiumConstants.ATTEMPT_SCOPE_VERIFY, username, ip);
	}
}
