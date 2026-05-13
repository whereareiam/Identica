package me.whereareiam.identica.integration.bstats.chart.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.integration.bstats.chart.type.NamedDrilldownPieChart;
import me.whereareiam.identica.model.provider.InternalProvider;
import me.whereareiam.identica.model.provider.ProviderDescriptor;
import me.whereareiam.identica.provider.ProviderManager;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Singleton
public final class ProvidersChart extends NamedDrilldownPieChart {
	private static final Set<String> OFFICIAL_PROVIDER_IDS = Set.of("password", "premium");

	private final ProviderManager providerManager;

	@Inject
	public ProvidersChart(@NotNull ProviderManager providerManager) {
		this.providerManager = providerManager;
	}

	@Override
	protected @NotNull String chartId() {
		return "providers";
	}

	@Override
	protected @NotNull Map<String, Integer> officialEntries() {
		return collect(true);
	}

	@Override
	protected @NotNull Map<String, Integer> customEntries() {
		return collect(false);
	}

	private @NotNull Map<String, Integer> collect(boolean official) {
		Map<String, Integer> values = new LinkedHashMap<>();
		List<InternalProvider> providers = providerManager.getProviders();
		for (InternalProvider provider : providers) {
			ProviderDescriptor descriptor = provider.getDescriptor();
			if (descriptor == null || descriptor.getId().isBlank())
				continue;

			boolean matches = OFFICIAL_PROVIDER_IDS.contains(descriptor.getId().trim().toLowerCase());
			if (matches != official)
				continue;

			values.put(displayName(descriptor), 1);
		}
		return values;
	}

	private @NotNull String displayName(@NotNull ProviderDescriptor descriptor) {
		String name = descriptor.getName();
		if (!name.isBlank()) return name.trim();

		return descriptor.getId().trim();
	}
}
