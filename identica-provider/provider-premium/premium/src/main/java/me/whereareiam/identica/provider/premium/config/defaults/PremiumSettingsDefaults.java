package me.whereareiam.identica.provider.premium.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.provider.premium.config.PremiumSettings;
import me.whereareiam.identica.provider.premium.type.PremiumDetectionMethod;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class PremiumSettingsDefaults implements DefaultsProvider<PremiumSettings> {
	@Override
	public PremiumSettings supply(PremiumSettings config) {
		PremiumSettings.Lookup lookup = new PremiumSettings.Lookup();
		lookup.setProfileEndpoint("https://api.mojang.com/users/profiles/minecraft/%s");
		lookup.setTimeout(Duration.ofSeconds(3));
		lookup.setCacheTtl(Duration.ofMinutes(5));

		PremiumSettings.Detection detection = new PremiumSettings.Detection();
		detection.setMethods(new ArrayList<>(List.of(PremiumDetectionMethod.LOOKUP)));
		detection.setRecheckOnMismatch(false);
		detection.setLookup(lookup);
		config.setDetection(detection);
		config.setProfileSnapshotTtl(Duration.ofMinutes(10));

		PremiumSettings.Cache cache = new PremiumSettings.Cache();
		cache.setProfile("premium-profile");
		cache.setProfileSnapshot("premium-profile-snapshot");
		PremiumSettings.Replication replication = new PremiumSettings.Replication();
		replication.setCache(cache);
		config.setReplication(replication);

		return config;
	}
}
