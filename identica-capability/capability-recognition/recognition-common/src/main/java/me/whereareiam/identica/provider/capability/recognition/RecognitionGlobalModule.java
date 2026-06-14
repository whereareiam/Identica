package me.whereareiam.identica.provider.capability.recognition;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.provider.capability.recognition.config.RecognitionSettings;
import me.whereareiam.identica.provider.capability.recognition.config.provider.RecognitionProvidersProvider;
import me.whereareiam.identica.provider.capability.recognition.config.provider.RecognitionSettingsProvider;
import me.whereareiam.identica.provider.capability.recognition.eligibility.DefaultRecognitionEligibilityRegistry;
import me.whereareiam.identica.provider.capability.recognition.eligibility.DefaultRecognitionEligibilityService;
import me.whereareiam.identica.provider.capability.recognition.eligibility.RecognitionEligibilityRegistry;
import me.whereareiam.identica.provider.capability.recognition.eligibility.RecognitionEligibilityService;
import me.whereareiam.identica.provider.capability.recognition.pipeline.RecognitionAppliedLifecycle;
import me.whereareiam.identica.provider.capability.recognition.pipeline.RecognitionPipelineExtension;
import me.whereareiam.identica.provider.capability.recognition.pipeline.RecognizedConnectionLifecycle;
import me.whereareiam.identica.provider.capability.recognition.store.DefaultRecognizedConnectionStore;
import me.whereareiam.identica.provider.capability.recognition.store.DefaultSessionRecognitionStore;
import me.whereareiam.identica.provider.capability.recognition.store.RecognizedConnectionStore;
import me.whereareiam.identica.provider.capability.recognition.store.SessionRecognitionStore;

import java.nio.file.Path;

@RequiredArgsConstructor
public class RecognitionGlobalModule extends AbstractModule {
	private final Path recognitionCapabilityPath;

	@Override
	protected void configure() {
		// Configuration
		bind(RecognitionSettingsProvider.class).asEagerSingleton();
		bind(RecognitionSettings.class).toProvider(RecognitionSettingsProvider.class);
		bind(RecognitionProvidersProvider.class).asEagerSingleton();

		// Services
		bind(SessionRecognitionStore.class).to(DefaultSessionRecognitionStore.class).asEagerSingleton();
		bind(SessionRecognitionService.class).to(DefaultSessionRecognitionService.class).asEagerSingleton();
		bind(RecognizedConnectionStore.class).to(DefaultRecognizedConnectionStore.class).asEagerSingleton();
		bind(RecognitionEligibilityRegistry.class).to(DefaultRecognitionEligibilityRegistry.class).asEagerSingleton();
		bind(RecognitionEligibilityService.class).to(DefaultRecognitionEligibilityService.class).asEagerSingleton();

		// Pipeline
		bind(RecognitionPipelineExtension.class).asEagerSingleton();
		bind(RecognitionAppliedLifecycle.class).asEagerSingleton();
		bind(RecognizedConnectionLifecycle.class).asEagerSingleton();
	}

	@Provides
	@Singleton
	@Named("recognitionCapabilityPath")
	Path provideRecognitionCapabilityPath() {
		return recognitionCapabilityPath;
	}
}
