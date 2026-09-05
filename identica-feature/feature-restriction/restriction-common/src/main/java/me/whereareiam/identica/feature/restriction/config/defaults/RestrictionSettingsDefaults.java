package me.whereareiam.identica.feature.restriction.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.feature.restriction.config.RestrictionSettings;

import java.time.Duration;

@Singleton
public class RestrictionSettingsDefaults implements DefaultsProvider<RestrictionSettings> {
	@Override
	public RestrictionSettings supply(RestrictionSettings settings) {
		settings.setToggleTtl(Duration.ofDays(365));

		RestrictionSettings.Replication replication = new RestrictionSettings.Replication();
		replication.setToggleNamespace("identica:restriction:toggle");
		settings.setReplication(replication);
		return settings;
	}
}
