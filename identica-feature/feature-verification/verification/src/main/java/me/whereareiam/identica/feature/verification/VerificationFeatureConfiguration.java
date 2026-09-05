package me.whereareiam.identica.feature.verification;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.feature.verification.database.VerificationDatabaseConfiguration;

import java.nio.file.Path;

@RequiredArgsConstructor
public class VerificationFeatureConfiguration extends AbstractModule {
	private final Path featuresPath;

	@Override
	protected void configure() {
		bind(Path.class).annotatedWith(Names.named("verificationFeaturePath"))
				.toInstance(featuresPath.resolve("verification"));
		install(new VerificationCommonConfiguration());
		install(new VerificationDatabaseConfiguration());
	}
}
