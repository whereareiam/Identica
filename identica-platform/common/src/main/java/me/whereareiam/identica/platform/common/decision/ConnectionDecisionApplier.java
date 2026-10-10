package me.whereareiam.identica.platform.common.decision;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.Serializer;
import me.whereareiam.identica.model.auth.ConnectionDecision;
import me.whereareiam.identica.model.config.Messages;
import me.whereareiam.identica.service.PlatformDeliveryAdapter;
import me.whereareiam.keystone.Actor;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ConnectionDecisionApplier {
	private final @NotNull Provider<Messages> messagesProvider;
	private final @NotNull ConnectionDecisionDeliveryCoordinator deliveryCoordinator;
	private final @NotNull PlatformDeliveryAdapter platformDeliveryAdapter;

	public void apply(
			@Nullable ConnectionDecision decision,
			@NotNull Actor actor,
			@NotNull Target target
	) {
		if (decision == null || decision.getStatus() == null) return;

		switch (decision.getStatus()) {
			case WAIT -> {
				String message = decision.getMessage();
				if (message == null || message.isBlank())
					return;

				actor.sendMessage(Serializer.serialize(actor, message));
			}
			case DENY -> target.deny(Serializer.serialize(actor, resolveAuthMessage(decision.getMessage())));
			case REQUIRE_RECONNECT -> target.requireReconnect(Serializer.serialize(actor, resolveAuthMessage(decision.getMessage())));
			default -> {
			}
		}
	}

	public boolean applyOrQueueWait(
			@Nullable ConnectionDecision decision,
			@NotNull Actor actor,
			@NotNull Target target,
			@NotNull UUID connectionUniqueId,
			@Nullable UUID accountUniqueId
	) {
		boolean deferred = deliveryCoordinator.queueWaitDecision(
				connectionUniqueId,
				accountUniqueId,
				decision,
				platformDeliveryAdapter.initialPromptCheckpoint()
		);
		if (!deferred)
			apply(decision, actor, target);

		return deferred;
	}

	private @NotNull String resolveAuthMessage(@Nullable String message) {
		if (message != null && !message.isBlank()) return message;
		return String.join("\n", messagesProvider.get().getScenarios().getAuthentication().getAuthenticationFailed());
	}

	public interface Target {
		void deny(@NotNull Component message);

		void requireReconnect(@NotNull Component message);
	}
}
