package me.whereareiam.identica.provider.password.completion;

import com.google.inject.Inject;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.pipeline.completion.extension.CompletionExtension;
import me.whereareiam.identica.pipeline.completion.extension.CompletionExtensionBuilder;
import me.whereareiam.identica.provider.password.PasswordConstants;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PasswordCompletionExtension implements CompletionExtension {
	private final PasswordCompletionStep passwordCompletionStep;

	public static @NotNull String extensionId() {
		return PasswordConstants.PROVIDER_ID + ":completion";
	}

	@Override
	public @NotNull String id() {
		return extensionId();
	}

	@Override
	public int order() {
		return 10;
	}

	@Override
	public void apply(@NotNull CompletionExtensionBuilder builder) {
		builder.registerStep(PasswordConstants.PROVIDER_ID, PipelineType.AUTHENTICATION, passwordCompletionStep);
		builder.registerStep(PasswordConstants.PROVIDER_ID, PipelineType.REGISTRATION, passwordCompletionStep);
		builder.registerStep(PasswordConstants.PROVIDER_ID, PipelineType.MIGRATION, passwordCompletionStep);
	}
}
