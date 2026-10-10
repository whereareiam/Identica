package me.whereareiam.identica.platform.bungeecord.listener.connection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.platform.common.adapter.PlatformLoginDecisionAdapter;
import net.md_5.bungee.api.event.PostLoginEvent;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class PostLoginListener implements DynamicListener<PostLoginEvent> {
	private final PlatformLoginDecisionAdapter<PostLoginEvent> loginDecisionAdapter;

	@Override
	public void onEvent(PostLoginEvent event) {
		loginDecisionAdapter.process(event);
	}
}
