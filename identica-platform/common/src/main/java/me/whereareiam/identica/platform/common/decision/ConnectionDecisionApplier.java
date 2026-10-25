package me.whereareiam.identica.platform.common.decision;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.message.MessageService;
import me.whereareiam.identica.message.ScenarioMessages;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.type.pipeline.PipelineType;
import me.whereareiam.keystone.Actor;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ConnectionDecisionApplier {
	private final @NotNull Provider<Messages> messagesProvider;
	private final @NotNull ConnectionDecisionDeliveryCoordinator deliveryCoordinator;
	private final @NotNull MessageService messageService;

	public void apply(
			@Nullable ConnectionDecision decision,
			@NotNull Actor actor,
			@NotNull Target target
	) {
		if (decision == null || decision.getStatus() == null) return;

		switch (decision.getStatus()) {
			case WAIT -> {
				PlayerMessage message = decision.getMessage();
				if (message == null || message.isEmpty())
					return;

				if (actor instanceof Identity identity)
					messageService.send(identity, message);
				else
					actor.sendMessage(Serializer.serialize(actor, message.getText(), message.getPlaceholders()));
			}
			case DENY -> target.deny(messageService.disconnectReason(actor, resolveAuthMessage(decision.getMessage())));
			case REQUIRE_RECONNECT -> target.requireReconnect(messageService.disconnectReason(actor, resolveAuthMessage(decision.getMessage())));
			default -> {
			}
		}
	}

	public boolean applyOrQueueWait(
			@Nullable ConnectionDecision decision,
			@NotNull Actor actor,
			@NotNull Target target,
			@NotNull ConnectionIdentity connection
	) {
		boolean deferred = deliveryCoordinator.queueWaitDecision(connection, decision);
		if (!deferred)
			apply(decision, actor, target);

		return deferred;
	}

	private @NotNull PlayerMessage resolveAuthMessage(@Nullable PlayerMessage message) {
		if (message != null && !message.isEmpty()) return message;
		return ScenarioMessages.of(PipelineType.AUTHENTICATION).getFailed().create(messagesProvider.get());
	}

	public interface Target {
		void deny(@NotNull Component message);

		void requireReconnect(@NotNull Component message);
	}
}
