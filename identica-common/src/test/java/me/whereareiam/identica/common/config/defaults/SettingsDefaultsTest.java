package me.whereareiam.identica.common.config.defaults;

import me.whereareiam.configura.Config;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.common.config.IdenticaModule;
import me.whereareiam.identica.model.Event;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.type.PlatformType;
import me.whereareiam.identica.type.event.EventPriority;
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

		assertNotNull(settings.getConnection());
		assertNotNull(settings.getConnection().getRouting());
		assertNotNull(settings.getConnection().getRouting().getScenarios());
		assertTrue(settings.getConnection().getRouting().getScenarios().isEmpty());
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

	@DisplayName("Generated settings file writes an empty scenarios map")
	@Test
	void generatedSettingsFileWritesEmptyScenariosMap(@TempDir Path tempDir) throws Exception {
		Path settingsPath = tempDir.resolve("settings.yml");
		Config config = Config.builder()
				.format(Format.YAML)
				.module(new IdenticaModule())
				.defaults(SettingsDefaults.class)
				.build();

		Settings settings = config.update(settingsPath, Settings.class);

		assertTrue(settings.getConnection().getRouting().getScenarios().isEmpty());
		String generated = Files.readString(settingsPath);
		assertTrue(generated.contains("scenarios: {}"));
		assertFalse(generated.contains("authentication:\n        step:"));
		assertFalse(generated.contains("registration:\n        step:"));
		assertFalse(generated.contains("migration:\n        step:"));
	}
}
