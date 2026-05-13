package me.whereareiam.identica.provider.password.command;

import com.google.inject.Inject;
import com.google.inject.Provider;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.ConnectionCoordinator;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.annotation.Argument;
import me.whereareiam.identica.annotation.Command;
import me.whereareiam.identica.annotation.Definition;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.auth.request.AdvanceRequest;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.pipeline.state.PipelineState;
import me.whereareiam.identica.model.pipeline.journey.JourneyStateItem;
import me.whereareiam.identica.model.pipeline.state.PipelineStateReference;
import me.whereareiam.identica.pipeline.state.PipelineStateStore;
import me.whereareiam.identica.provider.password.config.PasswordMessages;
import me.whereareiam.identica.provider.password.pipeline.PasswordAuthenticationAttempt;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.keystone.Actor;
import me.whereareiam.keystone.model.SerializerContent;
import org.jetbrains.annotations.NotNull;

@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LoginCommand {
	private final ConnectionCoordinator connectionCoordinator;
	private final PipelineStateStore pipelineStateStore;
	private final Provider<PasswordMessages> messagesProvider;
	private final Provider<Settings> settingsProvider;

	@Definition("login")
	@Command("login <password>")
	public void login(@NotNull Actor sender, @Argument(value = "password", parser = "password") String password) {
		if (!(sender instanceof Identity identity))
			return;

		PasswordMessages.Scenario.Authentication messages = messagesProvider.get().getScenario().getAuthentication();
		if (!hasPending(identity)) {
			sendMessage(identity, messages.getStatus().getNoPending());
			return;
		}

		PipelineType pipelineType = pendingPipelineType(identity);
		long ttlMs = resolveTtl(pipelineType);
		storeAuthenticationAttempt(identity, new PasswordAuthenticationAttempt(password), ttlMs);
		handleDecision(identity, messages, advanceJourneyMode(identity));
	}

	private void handleDecision(
			@NotNull Identity identity,
			@NotNull PasswordMessages.Scenario.Authentication messages,
			ConnectionDecision decision
	) {
		if (decision == null || decision.getStatus() == null)
			return;

		switch (decision.getStatus()) {
			case WAIT -> sendMessage(identity, decision.getMessage());
			case DENY, REQUIRE_RECONNECT -> disconnect(identity, decision.getMessage());
			case NO_PENDING -> sendMessage(identity, messages.getStatus().getNoPending());
			case ALLOW -> sendMessage(identity, messages.getStatus().getSuccess());
			default -> {
			}
		}
	}

	private ConnectionDecision advanceJourneyMode(@NotNull Identity identity) {
		AdvanceRequest request = AdvanceRequest.builder()
				.connectionUniqueId(identity.getUniqueId())
				.identity(identity)
				.build();
		return connectionCoordinator.advance(request).toCompletableFuture().join();
	}

	private boolean hasPending(@NotNull Identity identity) {
		PipelineState state = pipelineStateStore.find(reference(identity)).orElse(null);
		if (state == null)
			return false;
		return state.item(JourneyStateItem.class).isPresent();
	}

	private PipelineType pendingPipelineType(@NotNull Identity identity) {
		PipelineState state = pipelineStateStore.find(reference(identity)).orElse(null);
		PipelineType type = state != null ? state.getPipelineType() : null;
		return type != null ? type : PipelineType.AUTHENTICATION;
	}

	private long resolveTtl(@NotNull PipelineType pipelineType) {
		Settings settings = settingsProvider.get();
		if (settings == null)
			return 0L;
		Settings.Connection connection = settings.getConnection();
		if (pipelineType == PipelineType.MIGRATION)
			return connection.getMigration().pipelineTtlMillis();
		if (pipelineType == PipelineType.AUTHENTICATION)
			return connection.getAuthentication().pipelineTtlMillis();
		return connection.getRegistration().pipelineTtlMillis();
	}

	private PipelineStateReference reference(@NotNull Identity identity) {
		return PipelineStateReference.builder()
				.connectionUniqueId(identity.getUniqueId())
				.identityUniqueId(identity.getUniqueId())
				.build();
	}

	private void storeAuthenticationAttempt(
			@NotNull Identity identity,
			@NotNull PasswordAuthenticationAttempt attempt,
			long ttlMs
	) {
		if (ttlMs <= 0) return;
		PipelineStateReference reference = reference(identity);
		PipelineState stored = pipelineStateStore.find(reference).orElse(null);
		if (stored == null) return;

		stored.putItem(attempt, ttlMs);
		pipelineStateStore.save(reference, stored, ttlMs);
	}

	private void sendMessage(@NotNull Identity identity, String message) {
		if (message == null || message.isBlank())
			return;
		SerializerContent content = SerializerContent.builder()
				.receiver(identity)
				.message(message)
				.build();
		identity.sendMessage(Serializer.serialize(content));
	}

	private void disconnect(@NotNull Identity identity, String message) {
		if (message == null || message.isBlank())
			return;
		identity.disconnect(Serializer.serialize(identity, message));
	}

}
