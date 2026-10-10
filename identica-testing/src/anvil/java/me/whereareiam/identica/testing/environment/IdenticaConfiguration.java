package me.whereareiam.identica.testing.environment;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.common.config.IdenticaModule;
import me.whereareiam.identica.common.config.defaults.EngineDefaults;
import me.whereareiam.identica.common.config.defaults.RoutingDefaults;
import me.whereareiam.identica.common.config.defaults.SettingsDefaults;
import me.whereareiam.identica.common.config.defaults.provider.ProvidersDefaults;
import me.whereareiam.identica.model.config.Engine;
import me.whereareiam.identica.model.config.Routing;
import me.whereareiam.identica.model.config.Settings;
import me.whereareiam.identica.model.config.provider.Providers;
import me.whereareiam.identica.model.routing.attempt.RoutingAttemptPolicy;
import me.whereareiam.identica.provider.premium.config.PremiumSettings;
import me.whereareiam.identica.type.pipeline.journey.JourneyMode;
import me.whereareiam.identica.type.pipeline.journey.JourneyPolicy;
import me.whereareiam.identica.type.routing.RoutingRetryMode;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writes a proxy's configuration files. Each file is Identica's own model, filled with its defaults and changed
 * where the test network differs, then written as the plugin writes it.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class IdenticaConfiguration {
	/** Written as the plugin writes them, including Identica's own value types. */
	private static final Configura CONFIGURA = Config.builder().format(Format.YAML).module(new IdenticaModule()).build();
	private static final int DEBUG_LEVEL = 3;

	/**
	 * Returns the files every test proxy needs, by path inside Identica's data directory.
	 */
	static Map<String, String> files(
			JourneyMode mode,
			JourneyPolicy policy,
			Set<Provider> enabled,
			boolean entrypoints,
			boolean autoSelectSingleProvider,
			String step,
			String complete,
			YggdrasilMock yggdrasil
	) {
		Map<String, String> files = new LinkedHashMap<>();
		files.put("engine.yml", write(engine(mode, policy, autoSelectSingleProvider)));
		files.put("routing.yml", write(routing(step, complete)));
		files.put("settings.yml", write(settings()));
		files.put("providers/providers.yml", write(providers(enabled, entrypoints)));
		files.put("providers/Premium/settings.yml", write(premium(yggdrasil)));

		return files;
	}

	private static Engine engine(JourneyMode mode, JourneyPolicy policy, boolean autoSelectSingleProvider) {
		Engine engine = new EngineDefaults().supply(new Engine());
		Engine.Scenarios scenarios = engine.getScenarios();
		for (Engine.Scenario scenario : List.of(scenarios.getAuthentication(), scenarios.getRegistration())) {
			scenario.setJourneyMode(mode);
			scenario.setJourneyPolicy(policy);
		}
		scenarios.getRegistration().setAutoSelectSingleProvider(autoSelectSingleProvider);

		return engine;
	}

	/**
	 * Players stay on the step target while a step waits for them and reach the completion target when they are
	 * done; a blank target leaves them where they are. Completion routing retries until the player arrives.
	 */
	private static Routing routing(String step, String complete) {
		Routing routing = new RoutingDefaults().supply(new Routing());
		routing.getDefaults().getStep().setTarget(step);

		RoutingAttemptPolicy untilReached = new RoutingAttemptPolicy();
		untilReached.setMode(RoutingRetryMode.UNTIL_REACHED);
		untilReached.setRetryDelay(Duration.ofSeconds(1));
		untilReached.setConsumeOnReached(true);
		routing.getDefaults().getComplete().setTarget(complete);
		routing.getDefaults().getComplete().setAttempts(untilReached);
		routing.getScenarios().clear();

		return routing;
	}

	private static Settings settings() {
		Settings settings = new SettingsDefaults().supply(new Settings());
		settings.setLevel(DEBUG_LEVEL);

		return settings;
	}

	private static Providers providers(Set<Provider> enabled, boolean entrypoints) {
		Providers providers = new ProvidersDefaults().supply(new Providers());
		Map<String, Providers.ProviderEntry> known = providers.getProviders().stream()
				.collect(Collectors.toMap(Providers.ProviderEntry::getId, entry -> entry));

		List<Providers.ProviderEntry> entries = new ArrayList<>();
		for (Provider provider : Provider.values()) {
			Providers.ProviderEntry entry = known.get(provider.getId());
			entry.setEnabled(enabled.contains(provider));
			entry.setPriority(provider.getPriority());
			entry.setEntrypoints(entrypoints ? List.of(provider.getEntrypoint()) : List.of());
			entries.add(entry);
		}
		providers.setProviders(entries);

		return providers;
	}

	/**
	 * Premium asks the network's stand-in, not Mojang, whether a username is premium, and does not cache the answer.
	 */
	private static PremiumSettings premium(YggdrasilMock yggdrasil) {
		PremiumSettings settings = new PremiumSettings();
		settings.getLookup().setProfileEndpoint(yggdrasil.profileLookup() + "%s");
		settings.getLookup().setCacheTtl(Duration.ZERO);

		return settings;
	}

	static String write(Object document) {
		return new String(CONFIGURA.writeBytes(document), StandardCharsets.UTF_8);
	}
}
