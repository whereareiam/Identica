package me.whereareiam.identica.provider.credential.completion;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import me.whereareiam.identica.feature.FeatureRegistry;
import me.whereareiam.identica.feature.recognition.store.RecognizedConnectionStore;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.model.message.MessageDefinition;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.model.pipeline.completion.CompletionContext;
import me.whereareiam.identica.pipeline.completion.step.AbstractMessageCompletionStep;
import me.whereareiam.identica.provider.credential.CredentialMessageDefinitions;
import me.whereareiam.identica.provider.credential.config.CredentialMessages;
import me.whereareiam.identica.type.pipeline.PipelineType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Singleton
public class CredentialCompletionStep extends AbstractMessageCompletionStep {
	private final Provider<CredentialMessages> messagesProvider;
	private final MessageService messageService;

	private final @NotNull FeatureRegistry features;
	private final @NotNull Injector injector;

	@Inject
	public CredentialCompletionStep(
			Provider<CredentialMessages> messagesProvider,
			MessageService messageService,
			@NotNull FeatureRegistry features,
			@NotNull Injector injector
	) {
		super("password-completion");
		this.features = features;
		this.injector = injector;
		this.messagesProvider = messagesProvider;
		this.messageService = messageService;
	}

	@Override
	protected @Nullable AbstractMessageCompletionStep.TitleContent title(@NotNull CompletionContext context) {
		CredentialMessages.Completion.Pipeline messages = resolve(context);
		if (messages == null || messages.getTitle() == null) return null;

		return title(
				messages.getTitle().getTitle(),
				messages.getTitle().getSubtitle()
		);
	}

	@Override
	protected @Nullable PlayerMessage message(@NotNull CompletionContext context) {
		return definition(context).create(messagesProvider.get());
	}

	@Override
	protected @NotNull MessageService messageService() {
		return messageService;
	}

	private @NotNull MessageDefinition<CredentialMessages> definition(@NotNull CompletionContext context) {
		if (context.getPipelineType() == PipelineType.MIGRATION) return CredentialMessageDefinitions.COMPLETION_MIGRATION;
		if (context.getPipelineType() == PipelineType.REGISTRATION) return CredentialMessageDefinitions.COMPLETION_REGISTRATION;
		if (isRecognized(context)) return CredentialMessageDefinitions.COMPLETION_RECOGNITION;

		return CredentialMessageDefinitions.COMPLETION_AUTHENTICATION;
	}

	private @Nullable CredentialMessages.Completion.Pipeline resolve(@NotNull CompletionContext context) {
		CredentialMessages.Completion completion = messagesProvider.get().getCompletion();

		if (context.getPipelineType() == PipelineType.MIGRATION)
			return completion.getMigration();
		if (context.getPipelineType() == PipelineType.REGISTRATION)
			return completion.getRegistration();
		if (isRecognized(context))
			return completion.getRecognition();

		return completion.getAuthentication();
	}

	private boolean isRecognized(@NotNull CompletionContext context) {
		if (!features.isEnabled("credential", "recognition")) return false;
		UUID connectionUniqueId = context.getIdentity().getConnectionUniqueId();
		return connectionUniqueId != null && injector.getInstance(RecognizedConnectionStore.class).isRecognized(connectionUniqueId);
	}
}
