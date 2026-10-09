package me.whereareiam.identica.feature.recognition.config.provider;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.configura.annotation.merge.Merge;
import me.whereareiam.configura.feature.extension.api.annotation.ExtendableDocument;
import me.whereareiam.configura.merge.strategy.DeclaredObjectDefaults;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.feature.recognition.type.RecognitionSignal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Recognition feature provider-specific config extension.
 */
@Getter
@Setter
@ToString
public class RecognitionFeatures extends Providers.ProviderEntry.Features {
	@Merge(DeclaredObjectDefaults.class)
	@ExtendableDocument
	private @Nullable Recognition recognition;

	@Getter
	@Setter
	@ToString
	@ExtendableDocument
	public static class Recognition {
		private @Nullable Boolean enabled;
		private @NotNull List<RecognitionSignal> signals = new ArrayList<>();
		private boolean allowOnUntrustedIps;
	}
}
