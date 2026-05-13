package me.whereareiam.identica.platform.bungeecord.listener.connection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.platform.bungeecord.adapter.auth.BungeeCordHandshakeDecisionAdapter;
import net.md_5.bungee.api.event.PlayerHandshakeEvent;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PlayerHandshakeListener implements DynamicListener<PlayerHandshakeEvent> {
	private final BungeeCordHandshakeDecisionAdapter handshakeDecisionAdapter;

	@Override
	public void onEvent(PlayerHandshakeEvent event) {
		handshakeDecisionAdapter.process(event).toCompletableFuture().join();
	}
}
