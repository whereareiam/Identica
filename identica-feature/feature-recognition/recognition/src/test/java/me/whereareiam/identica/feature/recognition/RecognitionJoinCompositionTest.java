package me.whereareiam.identica.feature.recognition;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Modules;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.command.CommandService;
import me.whereareiam.identica.database.provider.ProviderLinkPersistenceService;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.feature.FeatureContext;
import me.whereareiam.identica.feature.FeatureProviderContext;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.ProviderFeatureContribution;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.feature.recognition.config.defaults.RecognitionSettingsDefaults;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionProvidersProvider;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionSettingsProvider;
import me.whereareiam.identica.feature.restriction.RestrictionFeature;
import me.whereareiam.identica.feature.restriction.RestrictionService;
import me.whereareiam.identica.feature.restriction.config.RestrictionSettings;
import me.whereareiam.identica.feature.restriction.config.defaults.RestrictionSettingsDefaults;
import me.whereareiam.identica.feature.restriction.config.provider.RestrictionSettingsProvider;
import me.whereareiam.identica.feature.restriction.join.JoinRestrictionFeature;
import me.whereareiam.identica.feature.restriction.join.JoinRestrictionType;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionCommands;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionMessages;
import me.whereareiam.identica.feature.restriction.join.config.defaults.JoinRestrictionCommandsDefaults;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionCommandsProvider;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionMessagesProvider;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionProvidersProvider;
import me.whereareiam.identica.feature.restriction.registry.RestrictionSignalRegistry;
import me.whereareiam.identica.feature.restriction.registry.type.RestrictionTypeRegistry;
import me.whereareiam.identica.feature.restriction.registry.type.RestrictionTypeResolverRegistry;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionSettings;
import me.whereareiam.identica.feature.restriction.join.config.provider.JoinRestrictionSettingsProvider;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.provider.ProviderManager;
import me.whereareiam.identica.provider.ProviderOperations;
import me.whereareiam.identica.replication.ReplicationSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecognitionJoinCompositionTest {
	@TempDir
	Path featuresPath;

	@Test
	void composesRealServicesAndProviderContributionsInOneRoot() {
		RestrictionFeature restriction = new RestrictionFeature();
		JoinRestrictionFeature join = new JoinRestrictionFeature();
		RecognitionFeature recognition = new RecognitionFeature();
		FeatureRegistry features = mock(FeatureRegistry.class);
		when(features.isAvailable("restriction-join")).thenReturn(true);
		Registry<Reloadable> reloadables = mock(Registry.class);
		CommandService commands = mock(CommandService.class);
		PipelineExtensionRegistry pipelines = mock(PipelineExtensionRegistry.class);
		RecognitionSettingsProvider recognitionSettings = mock(RecognitionSettingsProvider.class);
		when(recognitionSettings.get()).thenReturn(new RecognitionSettingsDefaults().supply(new RecognitionSettings()));
		RecognitionProvidersProvider recognitionProviders = mock(RecognitionProvidersProvider.class);
		when(recognitionProviders.get()).thenReturn(new Providers());
		RestrictionSettingsProvider restrictionSettings = mock(RestrictionSettingsProvider.class);
		when(restrictionSettings.get()).thenReturn(new RestrictionSettingsDefaults().supply(new RestrictionSettings()));
		JoinRestrictionProvidersProvider joinProviders = mock(JoinRestrictionProvidersProvider.class);
		when(joinProviders.get()).thenReturn(new Providers());
		JoinRestrictionCommands definitions = new JoinRestrictionCommandsDefaults().supply(new JoinRestrictionCommands());
		Set<String> registeredCommandKeys = Set.copyOf(definitions.getCommands().keySet());
		JoinRestrictionCommandsProvider joinCommands = mock(JoinRestrictionCommandsProvider.class);
		when(joinCommands.get()).thenReturn(definitions);
		JoinRestrictionSettingsProvider joinSettings = mock(JoinRestrictionSettingsProvider.class);
		when(joinSettings.get()).thenReturn(new JoinRestrictionSettings());
		JoinRestrictionMessagesProvider joinMessages = mock(JoinRestrictionMessagesProvider.class);
		when(joinMessages.get()).thenReturn(new JoinRestrictionMessages());
		FeatureContext composition = FeatureContext.builder().featuresPath(featuresPath).features(features).build();
		List<com.google.inject.Module> modules = new ArrayList<>(restriction.modules(composition));
		modules.addAll(join.modules(composition));
		modules.addAll(recognition.modules(composition));
		Injector root = Guice.createInjector(Modules.override(modules).with(new AbstractModule() {
			@Override
			protected void configure() {
				bind(new TypeLiteral<Registry<Reloadable>>() {}).toInstance(reloadables);
				bind(FeatureRegistry.class).toInstance(features);
				bind(EventManager.class).toInstance(mock(EventManager.class));
				bind(PipelineExtensionRegistry.class).toInstance(pipelines);
				bind(ProviderManager.class).toInstance(mock(ProviderManager.class));
				bind(ProviderOperations.class).toInstance(mock(ProviderOperations.class));
				bind(ProviderLinkPersistenceService.class).toInstance(mock(ProviderLinkPersistenceService.class));
				bind(Replication.class).toInstance(new Replication());
				bind(ReplicationSystem.class).toInstance(mock(ReplicationSystem.class, RETURNS_DEEP_STUBS));
				bind(CommandService.class).toInstance(commands);
				bind(RecognitionSettingsProvider.class).toInstance(recognitionSettings);
				bind(RecognitionProvidersProvider.class).toInstance(recognitionProviders);
				bind(RestrictionSettingsProvider.class).toInstance(restrictionSettings);
				bind(JoinRestrictionProvidersProvider.class).toInstance(joinProviders);
				bind(JoinRestrictionCommandsProvider.class).toInstance(joinCommands);
				bind(JoinRestrictionSettingsProvider.class).toInstance(joinSettings);
				bind(JoinRestrictionMessagesProvider.class).toInstance(joinMessages);
			}
		}));
		FeatureContext context = FeatureContext.builder().featuresPath(featuresPath).features(features).injector(root).build();
		restriction.initialize(context);
		join.initialize(context);
		recognition.initialize(context);

		RestrictionSignalRegistry signals = root.getInstance(RestrictionSignalRegistry.class);
		assertTrue(signals.find(JoinRestrictionType.TYPE, "linked").isPresent());
		assertTrue(signals.find(JoinRestrictionType.TYPE, "recognized").isPresent());
		assertNotNull(root.getInstance(RestrictionService.class));
		FeatureProviderContext providerContext = FeatureProviderContext.builder()
				.providerId("premium").workingPath(featuresPath).descriptor(new ProviderDescriptor()).features(features).build();
		List<com.google.inject.Module> providerModules = new ArrayList<>(join.providerModules(providerContext));
		providerModules.addAll(recognition.providerModules(providerContext));
		Injector provider = root.createChildInjector(providerModules);
		Set<ProviderFeatureContribution> contributions = provider.getInstance(Key.get(new TypeLiteral<Set<ProviderFeatureContribution>>() {}));
		assertEquals(Set.of("recognition", "restriction-join"), contributions.stream()
				.map(ProviderFeatureContribution::featureId).collect(Collectors.toSet()));
		assertSame(root.getInstance(SessionRecognitionService.class), provider.getInstance(SessionRecognitionService.class));

		recognition.shutdown(context);
		assertTrue(signals.find(JoinRestrictionType.TYPE, "recognized").isEmpty());
		assertTrue(signals.find(JoinRestrictionType.TYPE, "linked").isPresent());
		definitions.getCommands().clear();
		join.shutdown(context);
		restriction.shutdown(context);

		assertTrue(signals.signals(JoinRestrictionType.TYPE).isEmpty());
		assertTrue(root.getInstance(RestrictionTypeRegistry.class).values().isEmpty());
		assertTrue(root.getInstance(RestrictionTypeResolverRegistry.class).values().isEmpty());
		verify(reloadables).unregister(joinSettings);
		verify(commands).unregisterCommands(registeredCommandKeys);
		verify(pipelines).unregister("recognition:phases");
		verify(pipelines).unregister("restriction:join:phases");
	}
}
