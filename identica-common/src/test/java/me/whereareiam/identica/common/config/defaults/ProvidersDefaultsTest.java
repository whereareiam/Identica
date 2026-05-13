package me.whereareiam.identica.common.config.defaults;

import me.whereareiam.identica.model.config.Providers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("Providers Defaults")
class ProvidersDefaultsTest {
	@DisplayName("Premium provider owns its session TTL override")
	@Test
	void premiumProviderOwnsSessionTtlOverride() {
		Providers providers = new ProvidersDefaults().supply(new Providers());

		assertEquals(Duration.ofHours(12), provider(providers, "premium").getOverrides().getSessionTtl());
		assertNull(provider(providers, "password").getOverrides().getSessionTtl());
	}

	private Providers.ProviderEntry provider(Providers providers, String id) {
		return providers.getProviders().stream()
				.filter(entry -> entry.getId().equals(id))
				.findFirst()
				.orElseThrow();
	}
}
