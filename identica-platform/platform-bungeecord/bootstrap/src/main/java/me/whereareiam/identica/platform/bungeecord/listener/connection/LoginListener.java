package me.whereareiam.identica.platform.bungeecord.listener.connection;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.listener.DynamicListener;
import me.whereareiam.identica.platform.bungeecord.adapter.profile.BungeeCordProfilePrepareAdapter;
import net.md_5.bungee.api.event.LoginEvent;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class LoginListener implements DynamicListener<LoginEvent> {
	private final BungeeCordProfilePrepareAdapter profilePrepareAdapter;

	@Override
	public void onEvent(LoginEvent event) {
		profilePrepareAdapter.prepare(event);
	}
}
