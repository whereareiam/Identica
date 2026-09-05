package me.whereareiam.identica.feature.sentinel;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;

@RequiredArgsConstructor
public class SentinelFeatureConfiguration extends AbstractModule {
	private final Path featuresPath;

	@Override
	protected void configure() {
		bind(Path.class).annotatedWith(Names.named("sentinelFeaturePath"))
				.toInstance(featuresPath.resolve("sentinel"));
		install(new SentinelCommonConfiguration());
	}
}
