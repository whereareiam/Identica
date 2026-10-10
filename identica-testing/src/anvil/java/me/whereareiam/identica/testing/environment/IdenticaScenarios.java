package me.whereareiam.identica.testing.environment;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.integration.junit.AnvilScenarioFactory;
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
import me.whereareiam.identica.testing.fixture.Mojang;
import me.whereareiam.identica.type.routing.RoutingRetryMode;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the network an {@link Identica} declaration describes. Each configuration file is Identica's own model,
 * filled with its defaults and changed where the declaration or the test network differs, then written as the
 * plugin writes it.
 */
public final class IdenticaScenarios implements AnvilScenarioFactory<Identica> {
	/** Written as the plugin writes them, including Identica's own value types. */
	private static final Configura CONFIGURA = Config.builder().format(Format.YAML).module(new IdenticaModule()).build();
	private static final int DEBUG_LEVEL = 3;

	@Override
	public @NotNull AnvilScenario create(@NotNull Identica identica) {
		Set<Provider> enabled = EnumSet.noneOf(Provider.class);
		enabled.addAll(Arrays.asList(identica.providers()));

		Map<String, String> configuration = new LinkedHashMap<>();
		configuration.put("engine.yml", write(engine(identica)));
		configuration.put("routing.yml", write(routing()));
		configuration.put("settings.yml", write(settings()));
		configuration.put("providers/providers.yml", write(providers(enabled, identica.entrypoints())));
		configuration.put("providers/Premium/settings.yml", write(premium()));

		Mojang.service().reset();

		return IdenticaNetwork.velocity(name(identica, enabled), configuration);
	}

	private static String name(Identica identica, Set<Provider> enabled) {
		String providers = enabled.stream().map(Provider::getId).collect(Collectors.joining("+"));
		return String.join("-", "identica", identica.mode().name(), identica.policy().name(), providers,
				identica.entrypoints() ? "entrypoints" : "direct",
				identica.autoSelectSingleProvider() ? "autoselect" : "choice").toLowerCase(Locale.ROOT);
	}

	private static Engine engine(Identica identica) {
		Engine engine = new EngineDefaults().supply(new Engine());
		Engine.Scenarios scenarios = engine.getScenarios();
		for (Engine.Scenario scenario : List.of(scenarios.getAuthentication(), scenarios.getRegistration())) {
			scenario.setJourneyMode(identica.mode());
			scenario.setJourneyPolicy(identica.policy());
		}
		scenarios.getRegistration().setAutoSelectSingleProvider(identica.autoSelectSingleProvider());

		return engine;
	}

	/**
	 * Players stay on the auth server during steps and reach the lobby when they are done.
	 */
	private static Routing routing() {
		Routing routing = new RoutingDefaults().supply(new Routing());
		routing.getDefaults().getStep().setTarget(IdenticaNetwork.AUTH);

		RoutingAttemptPolicy untilReached = new RoutingAttemptPolicy();
		untilReached.setMode(RoutingRetryMode.UNTIL_REACHED);
		untilReached.setRetryDelay(Duration.ofSeconds(1));
		untilReached.setConsumeOnReached(true);
		routing.getDefaults().getComplete().setTarget(IdenticaNetwork.LOBBY);
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
	 * Premium asks the local Mojang service, not Mojang, whether a username is premium, and does not cache the answer.
	 */
	private static PremiumSettings premium() {
		PremiumSettings settings = new PremiumSettings();
		settings.getLookup().setProfileEndpoint(Mojang.service().profileLookup() + "%s");
		settings.getLookup().setCacheTtl(Duration.ZERO);

		return settings;
	}

	private static String write(Object document) {
		return new String(CONFIGURA.writeBytes(document), StandardCharsets.UTF_8);
	}
}
