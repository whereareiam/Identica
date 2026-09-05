package me.whereareiam.identica.common.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.model.Event;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.type.event.EventPriority;
import me.whereareiam.identica.type.identity.UniqueIdMode;
import me.whereareiam.identica.type.platform.PlatformType;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public class SettingsDefaults implements DefaultsProvider<Settings> {
	@Override
	public Settings supply(Settings settings) {
		settings.setLevel(2);
		settings.setIdentity(defaultIdentity());
		settings.setSessions(defaultSessions());

		Settings.Listeners listeners = new Settings.Listeners();
		listeners.setEvents(defaultListenerEvents());
		settings.setListeners(listeners);
		return settings;
	}

	private Settings.Identity defaultIdentity() {
		Settings.Identity identity = new Settings.Identity();
		identity.setUniqueIdMode(UniqueIdMode.RANDOM);
		identity.setReservationTtl(Duration.ofMinutes(15));
		return identity;
	}

	private Settings.Sessions defaultSessions() {
		Settings.Sessions sessions = new Settings.Sessions();
		sessions.setConcurrencyPolicy(SessionConcurrencyPolicy.REPLACE_EXISTING);
		sessions.setActiveTtl(Duration.ofHours(12));
		return sessions;
	}

	private Map<String, Event> defaultListenerEvents() {
		return defaultListenerEvents(PlatformType.getType());
	}

	Map<String, Event> defaultListenerEvents(PlatformType platformType) {
		return switch (platformType) {
			case BUNGEECORD -> defaultBungeeCordListenerEvents();
			case VELOCITY, UNKNOWN -> defaultVelocityListenerEvents();
		};
	}

	private Map<String, Event> defaultVelocityListenerEvents() {
		Map<String, Event> events = new LinkedHashMap<>();
		events.put("com.velocitypowered.api.event.connection.PreLoginEvent", defaultEvent());
		events.put("com.velocitypowered.api.event.player.GameProfileRequestEvent", defaultEvent());
		events.put("com.velocitypowered.api.event.connection.LoginEvent", defaultEvent());
		events.put("com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent", defaultEvent(EventPriority.HIGH));
		events.put("com.velocitypowered.api.event.player.ServerPreConnectEvent", defaultEvent(EventPriority.HIGH));
		events.put("com.velocitypowered.api.event.player.ServerPostConnectEvent", defaultEvent());
		events.put("com.velocitypowered.api.event.connection.DisconnectEvent", defaultEvent());
		return events;
	}

	private Map<String, Event> defaultBungeeCordListenerEvents() {
		Map<String, Event> events = new LinkedHashMap<>();
		events.put("net.md_5.bungee.api.event.PlayerHandshakeEvent", defaultEvent());
		events.put("net.md_5.bungee.api.event.LoginEvent", defaultEvent());
		events.put("net.md_5.bungee.api.event.PostLoginEvent", defaultEvent());
		events.put("net.md_5.bungee.api.event.ServerConnectEvent", defaultEvent(EventPriority.HIGH));
		events.put("net.md_5.bungee.api.event.ServerConnectedEvent", defaultEvent(EventPriority.HIGH));
		events.put("net.md_5.bungee.api.event.ServerSwitchEvent", defaultEvent());
		events.put("net.md_5.bungee.api.event.PlayerDisconnectEvent", defaultEvent());
		return events;
	}

	private Event defaultEvent() {
		return defaultEvent(EventPriority.NORMAL);
	}

	private Event defaultEvent(EventPriority priority) {
		return Event.builder()
				.register(true)
				.priority(priority)
				.build();
	}
}
