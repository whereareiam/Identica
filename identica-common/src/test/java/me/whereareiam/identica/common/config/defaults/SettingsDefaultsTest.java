package me.whereareiam.identica.common.config.defaults;

import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.common.config.IdenticaModule;
import me.whereareiam.identica.model.Event;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.type.event.EventPriority;
import me.whereareiam.identica.type.platform.PlatformType;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Settings Defaults")
class SettingsDefaultsTest {
	@DisplayName("Generated routing scenarios are empty by default")
	@Test
	void generatedRoutingScenariosAreEmptyByDefault() {
		Settings settings = new SettingsDefaults().supply(new Settings());

		assertNotNull(settings.getIdentity());
		assertEquals(SessionConcurrencyPolicy.REPLACE_EXISTING, settings.getSessions().getConcurrencyPolicy());
		assertEquals(java.time.Duration.ofMinutes(15), settings.getIdentity().getReservationTtl());
	}

	@DisplayName("Listener defaults match the active platform listener set")
	@Test
	void listenerDefaultsMatchPlatformListenerSet() {
		SettingsDefaults defaults = new SettingsDefaults();

		Map<String, Event> velocityEvents = defaults.defaultListenerEvents(PlatformType.VELOCITY);
		assertEquals(7, velocityEvents.size());
		assertEquals(EventPriority.NORMAL, velocityEvents.get("com.velocitypowered.api.event.connection.PreLoginEvent").getPriority());
		assertEquals(EventPriority.NORMAL, velocityEvents.get("com.velocitypowered.api.event.player.ServerPostConnectEvent").getPriority());
		assertEquals(EventPriority.HIGH, velocityEvents.get("com.velocitypowered.api.event.player.ServerPreConnectEvent").getPriority());

		Map<String, Event> bungeeCordEvents = defaults.defaultListenerEvents(PlatformType.BUNGEECORD);
		assertEquals(7, bungeeCordEvents.size());
		assertEquals(EventPriority.NORMAL, bungeeCordEvents.get("net.md_5.bungee.api.event.LoginEvent").getPriority());
		assertEquals(EventPriority.HIGH, bungeeCordEvents.get("net.md_5.bungee.api.event.ServerConnectEvent").getPriority());
		assertEquals(EventPriority.HIGH, bungeeCordEvents.get("net.md_5.bungee.api.event.ServerConnectedEvent").getPriority());
	}

	@DisplayName("Generated settings file writes the split settings shape")
	@Test
	void generatedSettingsFileWritesExpectedShape(@TempDir Path tempDir) throws Exception {
		Path settingsPath = tempDir.resolve("settings.yml");
		Configura yaml = Config.builder()
				.format(Format.YAML)
				.module(new IdenticaModule())
				.defaults(SettingsDefaults.class)
				.build();

		Settings settings = yaml.update(settingsPath, Settings.class);

		assertNotNull(settings.getIdentity());
		String generated = Files.readString(settingsPath);
		assertTrue(generated.contains("identity:"));
		assertTrue(generated.contains("sessions:"));
		assertFalse(generated.contains("connection:"));
	}
}
