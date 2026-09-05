package me.whereareiam.identica.feature.sentinel;

import me.whereareiam.identica.feature.sentinel.model.config.SentinelMessages;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelSettings;
import me.whereareiam.identica.feature.sentinel.type.ResumeSpamSentinelDefinition;
import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Resume Spam Sentinel Definition")
class ResumeSpamSentinelDefinitionTest {
	@DisplayName("Policy message supplier renders the configured seconds placeholder")
	@Test
	void policyMessageSupplierRendersConfiguredSecondsPlaceholder() {
		SentinelSettings settings = new SentinelSettings();
		SentinelSettings.Sentinels sentinels = new SentinelSettings.Sentinels();
		SentinelPolicy resumeSpam = new SentinelPolicy();
		resumeSpam.setEnabled(true);
		resumeSpam.setMaxAttempts(5);
		SentinelPolicy.Lockout lockout = new SentinelPolicy.Lockout();
		lockout.setEnabled(true);
		lockout.setDuration(Duration.ofSeconds(30));
		resumeSpam.setLockout(lockout);
		sentinels.setResumeSpam(resumeSpam);
		settings.setSentinels(sentinels);

		SentinelMessages messages = new SentinelMessages();
		SentinelMessages.ResumeSpam resumeSpamMessages = new SentinelMessages.ResumeSpam();
		resumeSpamMessages.setDenied(List.of("Please wait {seconds}s."));
		messages.setResumeSpam(resumeSpamMessages);

		ResumeSpamSentinelDefinition definition = new ResumeSpamSentinelDefinition( () -> settings, () -> messages);
		SentinelPolicy policy = definition.policy(SentinelContext.builder().username("player").ip("127.0.0.1").build());

		assertNotNull(policy.getLockout());
		assertNotNull(policy.getLockout().getMessageSupplier());
		assertEquals("resume-spam", definition.id());
		assertTrue(policy.getLockout().getMessageSupplier().apply(null, 12L).contains("12s"));
	}
}
