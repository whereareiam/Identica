package me.whereareiam.identica.feature.recognition.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.configura.ConfigDocument;
import me.whereareiam.identica.feature.recognition.type.RecognitionSignal;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;

@Getter
@Setter
@ToString
public class RecognitionSettings extends ConfigDocument {
	private boolean enabled;
	private @NotNull Duration validity;
	private @NotNull Duration window;
	private @NotNull List<RecognitionSignal> defaultSignals;
	private @NotNull Eligibility eligibility;
	private @NotNull Replication replication;

	public long validityMillis() {
		if (validity.isZero() || validity.isNegative())
			throw new IllegalStateException("features.recognition.settings.validity must be positive");

		return validity.toMillis();
	}

	public long windowMillis() {
		if (window.isZero() || window.isNegative())
			throw new IllegalStateException("features.recognition.settings.window must be positive");

		return window.toMillis();
	}

	@Getter
	@Setter
	@ToString
	public static class Eligibility {
		private @NotNull UntrustedIps untrustedIps;

		@Getter
		@Setter
		@ToString
		public static class UntrustedIps {
			private boolean enabled;
			private @NotNull List<String> entries;
		}
	}

	@Getter
	@Setter
	@ToString
	public static class Replication {
		private @NotNull String snapshotNamespace;
		private @NotNull String recognizedConnectionNamespace;
	}
}
