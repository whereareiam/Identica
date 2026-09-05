package me.whereareiam.identica.provider.premium.step;

import com.google.inject.Guice;
import com.google.inject.Injector;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.recognition.SessionRecognitionService;
import me.whereareiam.identica.feature.recognition.store.RecognizedConnectionStore;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.auth.AuthContext;
import me.whereareiam.identica.model.pipeline.journey.stage.step.StepResult;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.provider.premium.pipeline.step.type.authentication.PremiumRecognitionStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Premium Recognition Step")
class PremiumRecognitionStepTest {
	@DisplayName("Completes authentication when shared session recognition matches")
	@Test
	void completesWhenRecognitionMatches() {
		SessionRecognitionService recognitionService = mock(SessionRecognitionService.class);
		RecognizedConnectionStore recognizedConnectionStore = mock(RecognizedConnectionStore.class);
		when(recognitionService.matches(any(), any(), any(), any(), any())).thenReturn(true);
		PremiumRecognitionStep step = injector(recognitionService, recognizedConnectionStore).getInstance(PremiumRecognitionStep.class);
		AuthContext context = context();

		StepResult result = step.execute(context).join();

		assertEquals(StepResult.StepStatus.COMPLETE, result.getStatus());
		verify(recognitionService).matches("premium", "premium-subject", "whereareiam", "127.0.0.1", null);
		verify(recognizedConnectionStore).markRecognized(context.getIdentity().getConnectionUniqueId());
	}

	@DisplayName("Continues to verification when shared session recognition does not match")
	@Test
	void continuesWhenRecognitionDoesNotMatch() {
		SessionRecognitionService recognitionService = mock(SessionRecognitionService.class);
		RecognizedConnectionStore recognizedConnectionStore = mock(RecognizedConnectionStore.class);
		when(recognitionService.matches(any(), any(), any(), any(), any())).thenReturn(false);
		PremiumRecognitionStep step = injector(recognitionService, recognizedConnectionStore).getInstance(PremiumRecognitionStep.class);
		AuthContext context = context();

		StepResult result = step.execute(context).join();

		assertEquals(StepResult.StepStatus.CONTINUE, result.getStatus());
		verify(recognizedConnectionStore, never()).markRecognized(any());
	}

	@Test
	void continuesWithoutRecognitionBindings() {
		FeatureRegistry features = mock(FeatureRegistry.class);
		Injector injector = Guice.createInjector(binder -> binder.bind(FeatureRegistry.class).toInstance(features));

		StepResult result = injector.getInstance(PremiumRecognitionStep.class).execute(context()).join();

		assertEquals(StepResult.StepStatus.CONTINUE, result.getStatus());
	}

	private Injector injector(SessionRecognitionService service, RecognizedConnectionStore store) {
		FeatureRegistry features = mock(FeatureRegistry.class);
		when(features.isEnabled("premium", "recognition")).thenReturn(true);
		return Guice.createInjector(binder -> {
			binder.bind(FeatureRegistry.class).toInstance(features);
			binder.bind(SessionRecognitionService.class).toInstance(service);
			binder.bind(RecognizedConnectionStore.class).toInstance(store);
		});
	}

	private AuthContext context() {
		ConnectionIdentity identity = new ConnectionIdentity("whereareiam", "127.0.0.1");
		identity.setConnectionUniqueId(java.util.UUID.randomUUID());
		return AuthContext.builder()
				.identity(identity)
				.provider(ProviderContext.of("premium", "premium-subject", "whereareiam", null))
				.build();
	}
}
