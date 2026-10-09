package me.whereareiam.identica.feature.recognition.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.feature.recognition.type.RecognitionSignal;

import java.time.Duration;
import java.util.List;

@Singleton
public class RecognitionSettingsDefaults implements DefaultsProvider<RecognitionSettings> {
	@Override
	public RecognitionSettings supply(RecognitionSettings settings) {
		settings.setEnabled(false);
		settings.setValidity(Duration.ofHours(12));
		settings.setWindow(Duration.ofMinutes(10));
		settings.setDefaultSignals(List.of(
				RecognitionSignal.USERNAME,
				RecognitionSignal.IP,
				RecognitionSignal.VIRTUAL_HOST
		));

		RecognitionSettings.Eligibility.UntrustedIps untrustedIps = new RecognitionSettings.Eligibility.UntrustedIps();
		untrustedIps.setEnabled(true);
		untrustedIps.setEntries(List.of(
				"127.0.0.1",
				"::1",
				"10.0.0.0/8",
				"172.16.0.0/12",
				"192.168.0.0/16"
		));
		RecognitionSettings.Eligibility eligibility = new RecognitionSettings.Eligibility();
		eligibility.setUntrustedIps(untrustedIps);
		settings.setEligibility(eligibility);

		RecognitionSettings.Replication replication = new RecognitionSettings.Replication();
		replication.setSnapshotNamespace("identica:session-recognition:snapshot");
		replication.setRecognizedConnectionNamespace("identica:recognized-connection");
		settings.setReplication(replication);
		return settings;
	}
}
