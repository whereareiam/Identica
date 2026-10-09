package me.whereareiam.identica.trait.authoritative.username.conflict.resolver;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.conflict.ConflictService;
import me.whereareiam.identica.identity.session.SessionService;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.conflict.ConflictContext;
import me.whereareiam.identica.model.conflict.ConflictResolution;
import me.whereareiam.identica.trait.authoritative.username.UsernameConflictSchema;
import me.whereareiam.identica.trait.authoritative.username.conflict.UsernameConflictType;
import me.whereareiam.identica.trait.authoritative.username.model.AuthoritativeUsernameMessages;
import me.whereareiam.identica.trait.authoritative.username.model.conflict.UsernameConflictResult;
import me.whereareiam.identica.trait.authoritative.username.model.conflict.UsernameConflictSubject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class UsernameConflictResolver {
	private final ConflictService conflictService;
	private final UsernameConflictType conflictType;
	private final SessionService sessionService;
	private final Provider<AuthoritativeUsernameMessages> messagesProvider;

	public @NotNull UsernameConflictResult handle(
			@NotNull UsernameConflictSubject subject,
			@NotNull String currentEffectiveUsername
	) {
		ConflictContext context = conflictType.createContext(subject);
		if (context == null) return UsernameConflictResult.allowed(currentEffectiveUsername);

		ConflictResolution resolution = conflictService.resolve(context);
		if (resolution == null) return UsernameConflictResult.allowed(currentEffectiveUsername);

		String effectiveUsername = currentEffectiveUsername;
		if (Boolean.TRUE.equals(resolution.getEffect(UsernameConflictSchema.EFFECT_CLOSE_EXISTING, Boolean.class)))
			closeExisting(context);

		String existingEffectiveUsername = resolution.getEffect(
				UsernameConflictSchema.EFFECT_EXISTING_EFFECTIVE_USERNAME,
				String.class
		);
		if (existingEffectiveUsername != null && !existingEffectiveUsername.isBlank())
			applyExistingEffectiveUsername(context, existingEffectiveUsername);

		String incomingEffectiveUsername = resolution.getEffect(
				UsernameConflictSchema.EFFECT_INCOMING_EFFECTIVE_USERNAME,
				String.class
		);
		if (incomingEffectiveUsername != null && !incomingEffectiveUsername.isBlank())
			effectiveUsername = incomingEffectiveUsername;

		if (resolution.getDecision() == ConflictResolution.Decision.DENY)
			return UsernameConflictResult.denied(resolveMessage(resolution.getMessage()), effectiveUsername);

		return UsernameConflictResult.allowed(effectiveUsername);
	}

	private @NotNull String resolveMessage(@Nullable String message) {
		if (message != null && !message.isBlank()) return message;
		return String.join("\n", messagesProvider.get().getPipeline().getPolicy().getConflictDenied());
	}

	private void closeExisting(@NotNull ConflictContext context) {
		UUID uniqueId = context.getParticipantAttribute(
				UsernameConflictSchema.ROLE_EXISTING,
				UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_ACCOUNT_UNIQUE_ID
		);
		if (uniqueId == null) return;

		sessionService.close(uniqueId).join();
	}

	private void applyExistingEffectiveUsername(@NotNull ConflictContext context, @NotNull String effectiveUsername) {
		UUID uniqueId = context.getParticipantAttribute(
				UsernameConflictSchema.ROLE_EXISTING,
				UsernameConflictSchema.PARTICIPANT_ATTRIBUTE_ACCOUNT_UNIQUE_ID
		);
		if (uniqueId == null) return;

		sessionService.findByUniqueId(uniqueId)
				.thenCompose(found -> {
					if (found.isEmpty()) return CompletableFuture.completedFuture(null);

					Session session = found.get();
					session.setEffectiveUsername(effectiveUsername);
					return sessionService.open(session).thenApply(ignored -> null);
				}).join();
	}
}
