package me.whereareiam.identica.platform.velocity.listener.connection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.platform.common.adapter.PlatformProfileAdapter;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class GameProfileRequestListener implements DynamicListener<GameProfileRequestEvent> {
	private final PlatformProfileAdapter<GameProfileRequestEvent> profileRewriteAdapter;

	@Override
	public void onEvent(GameProfileRequestEvent event) {
		profileRewriteAdapter.apply(event);
	}
}
