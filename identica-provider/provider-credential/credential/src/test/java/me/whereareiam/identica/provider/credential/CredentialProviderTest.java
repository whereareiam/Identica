package me.whereareiam.identica.provider.credential;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.sentinel.SentinelDefinition;
import me.whereareiam.identica.feature.sentinel.SentinelService;
import me.whereareiam.identica.model.provider.dependency.ProviderLibrary;
import me.whereareiam.identica.pipeline.completion.extension.CompletionExtensionRegistry;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.provider.credential.command.CommandRegistrar;
import me.whereareiam.identica.provider.credential.completion.CredentialCompletionExtension;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.provider.credential.config.CredentialSettings;
import me.whereareiam.identica.provider.credential.pipeline.CredentialPipelineExtension;
import me.whereareiam.identica.provider.credential.sentinel.BruteForceSentinelDefinition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CredentialProviderTest {
	@Test
	void declaresOptionalParticipationAndOnlyCryptographyLibraries() {
		CredentialProvider provider = new CredentialProvider();
		assertTrue(provider.traits().isEmpty());

		assertEquals(Set.of("verification", "recognition", "restriction", "restriction-join", "sentinel"), provider.supportedFeatures());
		assertEquals(List.of("bcrypt", "argon2-jvm"), provider.libraries().getLibraries().stream().map(ProviderLibrary::getArtifactId).toList());
	}

	@Test
	void enablesAndDisablesWithoutSentinelBindings() {
		FeatureRegistry features = mock(FeatureRegistry.class);
		Injector injector = Guice.createInjector();
		CredentialProvider provider = provider(features, injector);

		assertTrue(provider.featureModules(features).isEmpty());
		assertDoesNotThrow(provider::onEnable);
		assertDoesNotThrow(provider::onDisable);
	}

	@Test
	@SuppressWarnings("unchecked")
	void installsAndRegistersBruteForceOnlyWhenSentinelIsInstalled() {
		FeatureRegistry features = mock(FeatureRegistry.class);
		when(features.isAvailable("sentinel")).thenReturn(true);
		Registry<SentinelDefinition> registry = mock(Registry.class);
		List<Module> modules = new ArrayList<>(new CredentialProvider().featureModules(features));
		modules.add(binder -> {
			binder.bind(FeatureRegistry.class).toInstance(features);
			binder.bind(EventManager.class).toInstance(mock(EventManager.class));
			binder.bind(SentinelService.class).toInstance(mock(SentinelService.class));
			binder.bind(Key.get(new TypeLiteral<Registry<SentinelDefinition>>() {})).toInstance(registry);
			binder.bind(CredentialSettings.class).toInstance(new CredentialSettings());
			binder.bind(CredentialMessages.class).toInstance(new CredentialMessages());
		});
		Injector injector = Guice.createInjector(modules);
		CredentialProvider provider = provider(features, injector);

		provider.onEnable();
		provider.onDisable();

		SentinelDefinition definition = injector.getInstance(BruteForceSentinelDefinition.class);
		verify(registry).register(definition);
		verify(registry).unregister(definition);
	}

	private CredentialProvider provider(FeatureRegistry features, Injector injector) {
		return new CredentialProvider(
				mock(CommandRegistrar.class),
				mock(PipelineExtensionRegistry.class),
				mock(CompletionExtensionRegistry.class),
				mock(CredentialPipelineExtension.class),
				mock(CredentialCompletionExtension.class),
				features,
				injector
		);
	}
}
