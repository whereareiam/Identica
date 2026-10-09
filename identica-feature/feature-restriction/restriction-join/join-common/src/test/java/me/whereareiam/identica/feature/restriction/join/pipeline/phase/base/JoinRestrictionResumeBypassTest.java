package me.whereareiam.identica.feature.restriction.join.pipeline.phase.base;

import me.whereareiam.identica.feature.restriction.RestrictionService;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionMessages;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionSettings;
import me.whereareiam.identica.feature.restriction.join.config.defaults.JoinRestrictionSettingsDefaults;
import me.whereareiam.identica.feature.restriction.join.pipeline.scenario.type.authentication.group.identity.phase.EnforceAuthenticationJoinRestrictionPhase;
import me.whereareiam.identica.feature.restriction.join.pipeline.scenario.type.registration.group.identity.phase.EnforceRegistrationJoinRestrictionPhase;
import me.whereareiam.identica.pipeline.state.PipelineState;
import me.whereareiam.identica.pipeline.state.scenario.shared.item.IdentityMetaItem;
import me.whereareiam.identica.provider.ProviderOperations;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class JoinRestrictionResumeBypassTest {
	@Test
	void defaultsEnforceRestrictionsAndEachScenarioOptsInSeparately() {
		JoinRestrictionSettings settings = new JoinRestrictionSettingsDefaults().supply(new JoinRestrictionSettings());
		RestrictionService restrictions = mock(RestrictionService.class);
		ProviderOperations providers = mock(ProviderOperations.class);
		AbstractEnforceJoinRestrictionPhase<?, ?> authentication = new EnforceAuthenticationJoinRestrictionPhase(
				restrictions, providers, JoinRestrictionMessages::new, () -> settings
		);
		AbstractEnforceJoinRestrictionPhase<?, ?> registration = new EnforceRegistrationJoinRestrictionPhase(
				restrictions, providers, JoinRestrictionMessages::new, () -> settings
		);
		PipelineState resumed = PipelineState.initial();
		resumed.putItem(new IdentityMetaItem(true), 0L);

		assertFalse(authentication.allowResumeBypass(settings, resumed));
		assertFalse(registration.allowResumeBypass(settings, resumed));
		settings.getResumeBypass().setAuthentication(true);
		assertTrue(authentication.allowResumeBypass(settings, resumed));
		assertFalse(registration.allowResumeBypass(settings, resumed));
		settings.getResumeBypass().setAuthentication(false);
		settings.getResumeBypass().setRegistration(true);
		assertFalse(authentication.allowResumeBypass(settings, resumed));
		assertTrue(registration.allowResumeBypass(settings, resumed));
		assertFalse(registration.allowResumeBypass(settings, PipelineState.initial()));
		resumed.putItem(new IdentityMetaItem(false), 0L);
		assertFalse(registration.allowResumeBypass(settings, resumed));
	}
}
