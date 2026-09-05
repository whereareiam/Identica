package me.whereareiam.identica.feature.recognition;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.util.Modules;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.event.EventManager;
import me.whereareiam.identica.feature.FeatureContext;
import me.whereareiam.identica.feature.FeatureProviderContext;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.IdenticaFeature;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.feature.recognition.config.defaults.RecognitionSettingsDefaults;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionProvidersProvider;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionSettingsProvider;
import me.whereareiam.identica.feature.recognition.eligibility.RecognitionEligibilityRegistry;
import me.whereareiam.identica.feature.recognition.model.SessionRecognitionSnapshot;
import me.whereareiam.identica.feature.recognition.pipeline.RecognitionAppliedLifecycle;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.cache.base.ReplicationCacheBuilder;
import me.whereareiam.identica.feature.recognition.type.RecognitionSignal;
import me.whereareiam.identica.feature.restriction.RestrictionService;
import me.whereareiam.identica.feature.restriction.registry.RestrictionSignalRegistry;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.provider.ProviderManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecognitionFeatureTest {
	@TempDir
	Path featuresPath;

	@Test
	void recognizesAndCleansUpWithoutRestrictionBindings() {
		RecognitionFeature feature = new RecognitionFeature();
		FeatureRegistry features = mock(FeatureRegistry.class);
		EventManager events = mock(EventManager.class);
		PipelineExtensionRegistry pipelines = mock(PipelineExtensionRegistry.class);
		Registry<Reloadable> reloadables = mock(Registry.class);
		RecognitionSettings settings = new RecognitionSettingsDefaults().supply(new RecognitionSettings());
		settings.setEnabled(true);
		settings.setDefaultSignals(List.of(RecognitionSignal.USERNAME, RecognitionSignal.IP));
		RecognitionSettingsProvider settingsProvider = mock(RecognitionSettingsProvider.class);
		when(settingsProvider.get()).thenReturn(settings);
		RecognitionProvidersProvider providersProvider = mock(RecognitionProvidersProvider.class);
		when(providersProvider.get()).thenReturn(new Providers());
		ReplicationSystem replication = mock(ReplicationSystem.class, RETURNS_DEEP_STUBS);
		ReplicationCacheBuilder snapshotBuilder = mock(ReplicationCacheBuilder.class, RETURNS_SELF);
		ReplicatedCache<SessionRecognitionSnapshot> snapshots = mock(ReplicatedCache.class);
		when(replication.cache(settings.getReplication().getSnapshotNamespace())).thenReturn(snapshotBuilder);
		doReturn(snapshots).when(snapshotBuilder).replicated(any());
		when(snapshots.get("premium|subject")).thenReturn(CompletableFuture.completedFuture(Optional.of(SessionRecognitionSnapshot.builder()
				.providerId("premium")
				.providerSubject("subject")
				.providerUsername("Player")
				.lastIp("203.0.113.10")
				.build())));
		FeatureContext composition = FeatureContext.builder().featuresPath(featuresPath).features(features).build();
		Injector injector = Guice.createInjector(Modules.override(feature.modules(composition)).with(new AbstractModule() {
			@Override
			protected void configure() {
				bind(new TypeLiteral<Registry<Reloadable>>() {}).toInstance(reloadables);
				bind(FeatureRegistry.class).toInstance(features);
				bind(EventManager.class).toInstance(events);
				bind(PipelineExtensionRegistry.class).toInstance(pipelines);
				bind(ProviderManager.class).toInstance(mock(ProviderManager.class));
				bind(RecognitionSettingsProvider.class).toInstance(settingsProvider);
				bind(RecognitionProvidersProvider.class).toInstance(providersProvider);
				bind(ReplicationSystem.class).toInstance(replication);
				bind(Replication.class).toInstance(new Replication());
			}
		}));
		FeatureContext context = FeatureContext.builder()
				.featuresPath(featuresPath).features(features).injector(injector).build();

		feature.initialize(context);

		assertTrue(injector.getInstance(SessionRecognitionService.class)
				.matches("premium", "subject", "Player", "203.0.113.10", null));
		assertNull(injector.getExistingBinding(Key.get(RestrictionService.class)));
		assertNull(injector.getExistingBinding(Key.get(RestrictionSignalRegistry.class)));
		assertTrue(feature.providerModules(FeatureProviderContext.builder().providerId("premium").workingPath(featuresPath)
					.descriptor(new me.whereareiam.identica.model.provider.ProviderDescriptor()).features(features).build()).isEmpty());
		assertEquals(3, injector.getInstance(RecognitionEligibilityRegistry.class).resolve().size());
		verify(events).register(injector.getInstance(RecognitionAppliedLifecycle.class));

		feature.shutdown(context);
		feature.shutdown(context);

		assertTrue(injector.getInstance(RecognitionEligibilityRegistry.class).resolve().isEmpty());
		verify(events).unregister(injector.getInstance(RecognitionAppliedLifecycle.class));
		verify(pipelines).unregister("recognition:phases");
		verify(reloadables).unregister(settingsProvider);
		verify(reloadables).unregister(providersProvider);
	}

}
