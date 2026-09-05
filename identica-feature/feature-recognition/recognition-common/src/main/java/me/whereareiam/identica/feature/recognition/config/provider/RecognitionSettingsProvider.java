package me.whereareiam.identica.feature.recognition.config.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.config.ConfigProvider;
import me.whereareiam.identica.feature.recognition.config.RecognitionSettings;
import me.whereareiam.identica.feature.recognition.config.defaults.RecognitionSettingsDefaults;

import java.nio.file.Path;

@Singleton
public class RecognitionSettingsProvider extends ConfigProvider<RecognitionSettings> {
	@Inject
	public RecognitionSettingsProvider(
			@Named("recognitionFeaturePath") Path recognitionFeaturePath,
			Registry<Reloadable> reloadables
	) {
		super(recognitionFeaturePath, "settings", RecognitionSettings.class, reloadables);
	}

	@Override
	protected Configura configura() {
		return Config.configured().withDefaults(RecognitionSettingsDefaults.class);
	}
}
