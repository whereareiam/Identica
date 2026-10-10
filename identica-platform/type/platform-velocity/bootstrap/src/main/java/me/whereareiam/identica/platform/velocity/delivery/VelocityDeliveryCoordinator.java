package me.whereareiam.identica.platform.velocity.delivery;

import com.google.inject.Singleton;
import me.whereareiam.identica.event.delivery.DeliveryCheckpointReachedEvent;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.service.PlatformDeliveryAdapter;
import me.whereareiam.identica.type.messaging.DeliveryCheckpoint;
import me.whereareiam.identica.util.EventUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Singleton
public class VelocityDeliveryCoordinator implements PlatformDeliveryAdapter {
	@Override
	public @NotNull DeliveryCheckpoint initialPromptCheckpoint() {
		return DeliveryCheckpoint.IDENTITY_ATTACHED;
	}

	@Override
	public void armInitialReady(@NotNull Identity identity, @Nullable String currentServer) {
		EventUtil.callEvent(new DeliveryCheckpointReachedEvent(
				DeliveryCheckpoint.PLATFORM_READY_INITIAL,
				identity,
				currentServer
		));
	}

	@Override
	public void clear(@NotNull UUID connectionUniqueId) {
	}
}
