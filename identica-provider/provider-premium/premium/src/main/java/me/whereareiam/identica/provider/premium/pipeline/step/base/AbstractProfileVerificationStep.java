package me.whereareiam.identica.provider.premium.pipeline.step.base;

import com.google.inject.Provider;
import me.whereareiam.identica.handshake.HandshakeStore;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.pipeline.journey.step.type.SeamlessStep;
import me.whereareiam.identica.provider.premium.config.PremiumMessages;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileSnapshot;
import me.whereareiam.identica.provider.premium.profile.PremiumProfileStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class AbstractProfileVerificationStep extends SeamlessStep {
	protected final Provider<PremiumMessages> messagesProvider;
	protected final PremiumProfileStore profileStore;
	protected final HandshakeStore handshakeStore;
	protected final Provider<Engine> engineProvider;

	protected AbstractProfileVerificationStep(
			String name,
			Provider<PremiumMessages> messagesProvider,
			PremiumProfileStore profileStore,
			HandshakeStore handshakeStore,
			Provider<Engine> engineProvider
	) {
		super(name);
		this.messagesProvider = messagesProvider;
		this.profileStore = profileStore;
		this.handshakeStore = handshakeStore;
		this.engineProvider = engineProvider;
	}

	protected @Nullable String readProfileId(@NotNull String username) {
		PremiumProfileSnapshot snapshot = profileStore.find(username);
		return snapshot != null ? snapshot.getProfileId() : null;
	}

	protected void clearProfileItem(@NotNull String username) {
		profileStore.clear(username);
	}

	protected StepResult failed() {
		return StepResult.failed(null);
	}

	protected long ttlMillis() {
		return engineProvider.get()
				.getBehavior()
				.handshakeInstructionTtlMillis();
	}
}
