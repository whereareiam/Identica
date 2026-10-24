package me.whereareiam.identica.example.provider.pipeline;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.example.provider.ExampleProvider;
import me.whereareiam.identica.example.provider.pipeline.step.ExamplePassphraseStep;
import me.whereareiam.identica.model.pipeline.journey.ProviderMethod;
import me.whereareiam.identica.pipeline.extension.PipelineExtension;
import me.whereareiam.identica.pipeline.extension.PipelineExtensionBuilder;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Offers the passphrase as this provider's method of registration and authentication: its one way to get either done.
 */
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ExamplePipelineExtension implements PipelineExtension {
	private final ExamplePassphraseStep passphraseStep;

	@Override
	public @NotNull String id() {
		return ExampleProvider.ID + ":passphrase";
	}

	@Override
	public void apply(@NotNull PipelineExtensionBuilder builder) {
		for (PipelineType scenario : List.of(PipelineType.AUTHENTICATION, PipelineType.REGISTRATION))
			builder.registerMethod(ProviderMethod.builder()
					.providerId(ExampleProvider.ID)
					.name("passphrase")
					.scenario(scenario)
					.step(passphraseStep)
					.build());
	}
}
