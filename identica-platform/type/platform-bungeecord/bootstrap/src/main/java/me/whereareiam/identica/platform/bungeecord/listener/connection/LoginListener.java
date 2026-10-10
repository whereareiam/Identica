package me.whereareiam.identica.platform.bungeecord.listener.connection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.platform.common.adapter.PlatformProfileAdapter;
import net.md_5.bungee.api.event.LoginEvent;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LoginListener implements DynamicListener<LoginEvent> {
	private final PlatformProfileAdapter<LoginEvent> profilePrepareAdapter;

	@Override
	public void onEvent(LoginEvent event) {
		profilePrepareAdapter.apply(event);
	}
}
