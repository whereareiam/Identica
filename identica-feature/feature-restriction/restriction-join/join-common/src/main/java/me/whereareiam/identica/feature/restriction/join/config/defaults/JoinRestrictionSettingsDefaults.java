package me.whereareiam.identica.feature.restriction.join.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.feature.restriction.join.config.JoinRestrictionSettings;

@Singleton
public class JoinRestrictionSettingsDefaults implements DefaultsProvider<JoinRestrictionSettings> {
	@Override
	public JoinRestrictionSettings supply(JoinRestrictionSettings settings) {
		settings.setResumeBypass(new JoinRestrictionSettings.ResumeBypass());
		return settings;
	}
}
