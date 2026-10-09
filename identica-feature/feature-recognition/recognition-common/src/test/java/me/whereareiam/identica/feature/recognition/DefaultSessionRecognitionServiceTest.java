package me.whereareiam.identica.feature.recognition;

import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionFeatures;
import me.whereareiam.identica.feature.recognition.config.provider.RecognitionProvidersProvider;
import me.whereareiam.identica.feature.recognition.eligibility.DefaultRecognitionEligibilityRegistry;
import me.whereareiam.identica.feature.recognition.eligibility.DefaultRecognitionEligibilityService;
import me.whereareiam.identica.feature.recognition.eligibility.rule.RecognitionEnabledEligibilityRule;
import me.whereareiam.identica.feature.recognition.eligibility.rule.UntrustedIpRecognitionEligibilityRule;
import me.whereareiam.identica.feature.recognition.model.SessionRecognitionSnapshot;
import me.whereareiam.identica.feature.recognition.store.SessionRecognitionStore;
import me.whereareiam.identica.feature.recognition.type.RecognitionSignal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Default Session Recognition Service")
class DefaultSessionRecognitionServiceTest {
	@DisplayName("Blocks provider recognition on an untrusted IP even when the stored snapshot matches")
	@Test
	void blocksRecognitionOnUntrustedIp() {
		RecognitionSettings settings = settings(List.of(RecognitionSignal.USERNAME, RecognitionSignal.IP), List.of("127.0.0.1"));
		RecognitionProvidersProvider providersProvider = providersProvider(false);
		SessionRecognitionStore store = store(snapshot("127.0.0.1", null, null));

		DefaultSessionRecognitionService service = service(settings, providersProvider, store);

		assertFalse(service.matches("credential", "subject-1", "whereareiam", "127.0.0.1", null));
	}

	@DisplayName("Allows provider recognition on an untrusted IP when provider recognition allows it")
	@Test
	void allowsRecognitionOnUntrustedIpWhenProviderRecognitionAllowsIt() {
		RecognitionSettings settings = settings(List.of(RecognitionSignal.USERNAME, RecognitionSignal.IP), List.of("127.0.0.1"));
		RecognitionProvidersProvider providersProvider = providersProvider(true);
		SessionRecognitionStore store = store(snapshot("127.0.0.1", null, null));

		DefaultSessionRecognitionService service = service(settings, providersProvider, store);

		assertTrue(service.matches("credential", "subject-1", "whereareiam", "127.0.0.1", null));
	}

	@DisplayName("Keeps trusted IP recognition working for matching snapshots")
	@Test
	void keepsTrustedIpRecognitionWorking() {
		RecognitionSettings settings = settings(
				List.of(RecognitionSignal.USERNAME, RecognitionSignal.IP, RecognitionSignal.VIRTUAL_HOST),
				List.of("127.0.0.1")
		);
		RecognitionProvidersProvider providersProvider = providersProvider(false);
		SessionRecognitionStore store = store(snapshot("203.0.113.10", "localhost", 25565));

		DefaultSessionRecognitionService service = service(settings, providersProvider, store);

		assertTrue(service.matches(
				"credential",
				"subject-1",
				"whereareiam",
				"203.0.113.10",
				new ConnectionIdentity.Origin("localhost", 25565)
		));
	}

	private DefaultSessionRecognitionService service(
			RecognitionSettings settings,
			RecognitionProvidersProvider providersProvider,
			SessionRecognitionStore store
	) {
		DefaultRecognitionEligibilityRegistry registry = new DefaultRecognitionEligibilityRegistry();
		registry.register(new RecognitionEnabledEligibilityRule(() -> settings, providersProvider));
		registry.register(new UntrustedIpRecognitionEligibilityRule(() -> settings, providersProvider));

		return new DefaultSessionRecognitionService(
				() -> settings,
				providersProvider,
				store,
				new DefaultRecognitionEligibilityService(registry)
		);
	}

	private SessionRecognitionStore store(SessionRecognitionSnapshot snapshot) {
		SessionRecognitionStore store = mock(SessionRecognitionStore.class);
		when(store.find(snapshot.getProviderId(), snapshot.getProviderSubject())).thenReturn(Optional.of(snapshot));
		return store;
	}

	private RecognitionSettings settings(
			List<RecognitionSignal> signals,
			List<String> untrustedIps
	) {
		RecognitionSettings.Eligibility.UntrustedIps untrusted = new RecognitionSettings.Eligibility.UntrustedIps();
		untrusted.setEnabled(true);
		untrusted.setEntries(untrustedIps);

		RecognitionSettings.Eligibility eligibility = new RecognitionSettings.Eligibility();
		eligibility.setUntrustedIps(untrusted);

		RecognitionSettings settings = new RecognitionSettings();
		settings.setEnabled(true);
		settings.setValidity(Duration.ofHours(12));
		settings.setDefaultSignals(signals);
		settings.setEligibility(eligibility);
		return settings;
	}

	private RecognitionProvidersProvider providersProvider(boolean allowOnUntrustedIps) {
		RecognitionProvidersProvider provider = mock(RecognitionProvidersProvider.class);
		Providers providers = new Providers();
		Providers.ProviderEntry credential = new Providers.ProviderEntry();
		credential.setId("credential");
		RecognitionFeatures features = new RecognitionFeatures();
		RecognitionFeatures.Recognition recognition = new RecognitionFeatures.Recognition();
		recognition.setEnabled(true);
		recognition.setAllowOnUntrustedIps(allowOnUntrustedIps);
		features.setRecognition(recognition);
		credential.setFeatures(features);
		providers.setProviders(List.of(credential));
		when(provider.get()).thenReturn(providers);
		return provider;
	}

	private SessionRecognitionSnapshot snapshot(
			String lastIp,
			String lastVirtualHost,
			Integer lastVirtualPort
	) {
		return SessionRecognitionSnapshot.builder()
				.providerId("credential")
				.providerSubject("subject-1")
				.providerUsername("whereareiam")
				.lastIp(lastIp)
				.lastVirtualHost(lastVirtualHost)
				.lastVirtualPort(lastVirtualPort)
				.capturedAt(System.currentTimeMillis())
				.build();
	}
}
