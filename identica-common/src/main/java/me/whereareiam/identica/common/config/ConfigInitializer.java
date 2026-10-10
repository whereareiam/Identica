package me.whereareiam.identica.common.config;

import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import me.whereareiam.identica.config.ConfigProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class ConfigInitializer {
	public static List<String> initialize(Injector injector) {
		return initialize(injector, null);
	}

	public static List<String> initialize(Injector injector, Predicate<Class<?>> filter) {
		if (injector == null) return List.of();

		List<ConfigBinding> configBindings = injector.getAllBindings().keySet().stream()
				.filter(key -> ConfigProvider.class.isAssignableFrom(key.getTypeLiteral().getRawType()))
				.filter(key -> filter == null || filter.test(key.getTypeLiteral().getRawType()))
				.map(key -> new ConfigBinding(key, key.getTypeLiteral().getRawType()))
				.sorted(Comparator.comparing(ConfigBinding::type, ConfigLoadOrder.BY_TYPE))
				.toList();

		List<String> prepared = new ArrayList<>();
		for (ConfigBinding binding : configBindings) {
			Object instance = injector.getInstance(binding.key());
			if (instance instanceof Provider<?> provider) {
				provider.get();
				prepared.add(binding.type().getSimpleName());
			}
		}

		return prepared;
	}

	private record ConfigBinding(Key<?> key, Class<?> type) {
	}
}
