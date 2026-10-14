package me.whereareiam.identica.feature.sentinel;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.event.EventListener;
import me.whereareiam.identica.event.base.IdenticEvent;
import me.whereareiam.identica.event.connection.UsernameHeldEvent;
import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.type.ContestedConnectionSentinelDefinition;
import org.jetbrains.annotations.NotNull;

/**
 * Counts every connection refused because another connection logs in under its username as an attempt of its
 * address.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class ContestedLoginSentinelLifecycle implements EventListener {
	private final SentinelService sentinelService;

	@IdenticEvent
	public void onUsernameHeld(@NotNull UsernameHeldEvent event) {
		sentinelService.record(ContestedConnectionSentinelDefinition.ID, SentinelContext.builder()
				.connectionUniqueId(event.getConnectionUniqueId())
				.username(event.getUsername())
				.ip(event.getIp())
				.build());
	}
}
