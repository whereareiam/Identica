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

		List<ConfigBinding> configBindings = injector.getAllBindings().entrySet().stream()
				.filter(entry -> ConfigProvider.class.isAssignableFrom(entry.getKey().getTypeLiteral().getRawType()))
				.filter(entry -> filter == null || filter.test(entry.getKey().getTypeLiteral().getRawType()))
				.map(entry -> new ConfigBinding(entry.getKey(), entry.getKey().getTypeLiteral().getRawType()))
				.sorted(Comparator.comparing(binding -> binding.type().getSimpleName()))
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
