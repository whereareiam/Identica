package me.whereareiam.identica.provider.credential;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.BuildConfig;
import me.whereareiam.identica.Registry;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.sentinel.SentinelDefinition;
import me.whereareiam.identica.model.provider.dependency.ProviderLibraries;
import me.whereareiam.identica.model.provider.dependency.ProviderLibrary;
import me.whereareiam.identica.pipeline.completion.extension.CompletionExtensionRegistry;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionRegistry;
import me.whereareiam.identica.provider.IdenticaProvider;
import me.whereareiam.identica.provider.credential.command.CommandRegistrar;
import me.whereareiam.identica.provider.credential.completion.CredentialCompletionExtension;
import me.whereareiam.identica.provider.credential.cryptography.CryptographyModule;
import me.whereareiam.identica.provider.credential.cryptography.argon2.Argon2CryptographyModule;
import me.whereareiam.identica.provider.credential.cryptography.bcrypt.BcryptCryptographyModule;
import me.whereareiam.identica.provider.credential.database.DatabaseModule;
import me.whereareiam.identica.provider.credential.pipeline.CredentialPipelineExtension;
import me.whereareiam.identica.provider.credential.sentinel.BruteForceSentinelDefinition;
import me.whereareiam.identica.provider.credential.sentinel.CredentialSentinelModule;
import me.whereareiam.identica.type.provider.ProviderTrait;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

@NoArgsConstructor
@SuppressWarnings("unused")
@AllArgsConstructor(onConstructor_ = @Inject)
public class CredentialProvider extends IdenticaProvider {
	private CommandRegistrar commandRegistrar;
	private PipelineExtensionRegistry pipelineExtensionRegistry;
	private CompletionExtensionRegistry completionExtensionRegistry;
	private CredentialPipelineExtension credentialPipelineExtension;
	private CredentialCompletionExtension credentialCompletionExtension;
	private @NotNull FeatureRegistry features;
	private @NotNull Injector injector;

	@Override
	public @NotNull List<Module> modules() {
		return List.of(
				new CommonConfiguration(),
				new DatabaseModule(),
				new CryptographyModule(),
				new BcryptCryptographyModule(),
				new Argon2CryptographyModule()
		);
	}

	@Override
	public @NotNull List<Module> featureModules(@NotNull FeatureRegistry features) {
		return features.isAvailable("sentinel") ? List.of(new CredentialSentinelModule()) : List.of();
	}

	@Override
	public @NotNull Set<ProviderTrait> traits() {
		return Set.of();
	}

	@Override
	public @NotNull Set<String> supportedFeatures() {
		return Set.of("verification", "recognition", "restriction", "restriction-join", "sentinel");
	}

	@Override
	public @NotNull ProviderLibraries libraries() {
		ProviderLibraries libraries = new ProviderLibraries();
		libraries.setLibraries(List.of(
				ProviderLibrary.builder()
						.groupId("at.favre.lib")
						.artifactId("bcrypt")
						.version(BuildConfig.BCRYPT)
						.build(),
				ProviderLibrary.builder()
						.groupId("de.mkammerer")
						.artifactId("argon2-jvm")
						.version(BuildConfig.ARGON2)
						.build()
		));
		return libraries;
	}

	@Override
	public void onEnable() {
		commandRegistrar.registerCommands();
		pipelineExtensionRegistry.register(credentialPipelineExtension);
		completionExtensionRegistry.register(credentialCompletionExtension);
		if (features.isAvailable("sentinel"))
			injector.getInstance(Key.get(new TypeLiteral<Registry<SentinelDefinition>>() {}))
					.register(injector.getInstance(BruteForceSentinelDefinition.class));
	}

	@Override
	public void onDisable() {
		pipelineExtensionRegistry.unregister(CredentialPipelineExtension.extensionId());
		completionExtensionRegistry.unregister(CredentialCompletionExtension.extensionId());
		if (features.isAvailable("sentinel"))
			injector.getInstance(Key.get(new TypeLiteral<Registry<SentinelDefinition>>() {}))
					.unregister(injector.getInstance(BruteForceSentinelDefinition.class));
	}
}
