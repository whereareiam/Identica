package me.whereareiam.identica.feature.verification;

import me.whereareiam.identica.feature.verification.config.VerificationMessages;
import me.whereareiam.identica.feature.verification.config.defaults.VerificationDefaults;
import me.whereareiam.identica.feature.verification.database.VerificationPersistenceService;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import me.whereareiam.identica.feature.verification.type.VerificationMethodCapability;
import me.whereareiam.identica.feature.verification.type.totp.TotpClock;
import me.whereareiam.identica.feature.verification.type.totp.TotpVerificationMethod;
import me.whereareiam.identica.feature.verification.type.totp.process.TotpChallengeProcess;
import me.whereareiam.identica.feature.verification.type.totp.process.TotpEnrollmentProcess;
import me.whereareiam.identica.feature.verification.type.totp.step.TotpChallengeVerificationStep;
import me.whereareiam.identica.feature.verification.type.totp.step.TotpConfirmCodeStep;
import me.whereareiam.identica.feature.verification.type.totp.step.TotpConfirmSavedStep;
import me.whereareiam.identica.feature.verification.type.totp.step.TotpSetupStep;
import org.junit.jupiter.api.Test;

import java.time.InstantSource;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DefaultVerificationServiceTest {
	private static final TotpClock CLOCK = new TotpClock(InstantSource.system());

	@Test
	void registryFindsRegisteredMethodByDescriptorId() {
		VerificationSettings verification = new VerificationDefaults().supply(new VerificationSettings());
		VerificationMessages messages = messages();
		VerificationPersistenceService persistenceService = mock(VerificationPersistenceService.class);
		TotpSetupStep setupStep = new TotpSetupStep(() -> verification, () -> messages);
		TotpConfirmCodeStep confirmCodeStep = new TotpConfirmCodeStep(() -> verification, CLOCK);
		TotpConfirmSavedStep confirmSavedStep = new TotpConfirmSavedStep();
		TotpChallengeVerificationStep challengeStep = new TotpChallengeVerificationStep(() -> verification, persistenceService, CLOCK);
		TotpVerificationMethod method = new TotpVerificationMethod(
				() -> verification,
				new TotpEnrollmentProcess(setupStep, confirmCodeStep, confirmSavedStep),
				new TotpChallengeProcess(challengeStep)
		);

		DefaultVerificationRegistry registry = new DefaultVerificationRegistry(Set.of(method));

		assertTrue(registry.find("totp").isPresent());
		assertEquals("totp", registry.find("TOTP").orElseThrow().descriptor().getId());
	}

	@Test
	void builtInTotpDescriptorAdvertisesProcessCapabilities() {
		VerificationSettings verification = new VerificationDefaults().supply(new VerificationSettings());
		VerificationMessages messages = messages();
		VerificationPersistenceService persistenceService = mock(VerificationPersistenceService.class);
		TotpSetupStep setupStep = new TotpSetupStep(() -> verification, () -> messages);
		TotpConfirmCodeStep confirmCodeStep = new TotpConfirmCodeStep(() -> verification, CLOCK);
		TotpConfirmSavedStep confirmSavedStep = new TotpConfirmSavedStep();
		TotpChallengeVerificationStep challengeStep = new TotpChallengeVerificationStep(() -> verification, persistenceService, CLOCK);
		TotpVerificationMethod method = new TotpVerificationMethod(
				() -> verification,
				new TotpEnrollmentProcess(setupStep, confirmCodeStep, confirmSavedStep),
				new TotpChallengeProcess(challengeStep)
		);

		assertTrue(method.descriptor().isBuiltIn());
		assertTrue(method.descriptor().isUserEnrollable());
		assertTrue(method.descriptor().getCapabilities().contains(VerificationMethodCapability.CHALLENGE));
		assertTrue(method.descriptor().getCapabilities().contains(VerificationMethodCapability.RECOVERY_CODES));
	}

	private VerificationMessages messages() {
		VerificationMessages messages = new VerificationMessages();
		VerificationMessages.Methods.Totp totp = new VerificationMessages.Methods.Totp();
		totp.setPending(java.util.List.of("TOTP secret {secret}"));
		VerificationMessages.Methods methods = new VerificationMessages.Methods();
		methods.setTotp(totp);
		messages.setMethods(methods);
		return messages;
	}
}
