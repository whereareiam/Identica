package me.whereareiam.identica.feature.verification;

import me.whereareiam.configura.Config;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.feature.verification.config.defaults.VerificationDefaults;
import me.whereareiam.identica.feature.verification.config.provider.VerificationProvidersProvider;
import me.whereareiam.identica.feature.verification.model.config.VerificationSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class VerificationProvidersDefaultsTest {
	@Mock Registry<Reloadable> reloadables;

	@Test
	void providerOverridesInheritGlobalPolicyWithoutCopyingDefaultsIntoProviderFiles(@TempDir Path directory) throws Exception {
		Files.writeString(directory.resolve("providers.yml"), """
				providers:
				  - id: credential
				    features:
				      verification:
				        enabled: false
				  - id: premium
				    features:
				      verification:
				        enabled: true
				        required: false
				        unavailableSelectionPolicy: CLEAR_SELECTION
				  - id: community
				    features: {}
				  - id: no-methods
				    features:
				      verification:
				        methods: []
				""");
		var provider = new VerificationProvidersProvider(directory, reloadables);
		Files.writeString(directory.resolve("settings.yml"), "defaults:\n  enabled: true\n  required: true\n");
		var settings = new me.whereareiam.identica.feature.verification.config.provider.VerificationSettingsProvider(directory, reloadables).get();
		var resolver = new VerificationPolicyResolver(provider, () -> settings);
		assertFalse(resolver.resolveProviderPolicy("credential").enabled());
		assertTrue(resolver.resolveProviderPolicy("premium").enabled());
		assertFalse(resolver.resolveProviderPolicy("premium").required());
		assertEquals(me.whereareiam.identica.feature.verification.type.UnavailableSelectionPolicy.CLEAR_SELECTION, resolver.resolveMethodPolicy("premium", "totp").unavailableSelectionPolicy());
		assertTrue(resolver.resolveProviderPolicy("community").enabled());
		assertTrue(resolver.resolveProviderPolicy("community").required());
		assertTrue(resolver.resolveMethodPolicy("community", "totp").enabled());
		assertNull(resolver.resolveMethodPolicy("no-methods", "totp"));
		settings.getDefaults().setEnabled(false);
		assertFalse(resolver.resolveProviderPolicy("community").enabled());
		assertTrue(resolver.resolveProviderPolicy("premium").enabled());
		var nodes = Config.configured().readNode(directory.resolve("providers.yml")).get("providers");
		assertFalse(nodes.get(2).path("features").has("verification"));
	}
}
