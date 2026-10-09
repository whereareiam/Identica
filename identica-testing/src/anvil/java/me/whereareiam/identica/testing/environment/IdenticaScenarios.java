package me.whereareiam.identica.testing.environment;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.integration.junit.AnvilScenarioFactory;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the network an {@link Identica} declaration describes and renders the configuration files that differ
 * from Identica's generated defaults.
 */
public final class IdenticaScenarios implements AnvilScenarioFactory<Identica> {
	@Override
	public @NotNull AnvilScenario create(@NotNull Identica identica) {
		Set<Provider> enabled = EnumSet.noneOf(Provider.class);
		enabled.addAll(Arrays.asList(identica.providers()));

		Map<String, String> configuration = new LinkedHashMap<>();
		configuration.put("engine.yml", engine(identica));
		configuration.put("routing.yml", routing());
		configuration.put("providers/providers.yml", providers(enabled, identica.entrypoints()));

		return IdenticaNetwork.velocity(name(identica, enabled), configuration);
	}

	private static String name(Identica identica, Set<Provider> enabled) {
		String providers = enabled.stream().map(Provider::getId).collect(Collectors.joining("+"));
		return String.join("-", "identica", identica.mode().name(), identica.policy().name(), providers,
				identica.entrypoints() ? "entrypoints" : "direct",
				identica.autoSelectSingleProvider() ? "autoselect" : "choice").toLowerCase(Locale.ROOT);
	}

	private static String engine(Identica identica) {
		String scenario = """
				    journeyMode: "%s"
				    journeyPolicy: "%s"
				""".formatted(identica.mode(), identica.policy());
		return "scenarios:\n  authentication:\n" + scenario + "  registration:\n" + scenario
				+ "    autoSelectSingleProvider: " + identica.autoSelectSingleProvider() + "\n";
	}

	private static String routing() {
		return """
				defaults:
				  step:
				    target: "%1$s"
				  complete:
				    target: "%2$s"
				    attempts:
				      mode: "UNTIL_REACHED"
				      retryDelay: "1s"
				      consumeOnReached: true
				scenarios: {}
				""".formatted(IdenticaNetwork.AUTH, IdenticaNetwork.LOBBY);
	}

	private static String providers(Set<Provider> enabled, boolean entrypoints) {
		StringBuilder yaml = new StringBuilder("providers:\n");
		for (Provider provider : Provider.values()) {
			yaml.append("  - id: \"").append(provider.getId()).append("\"\n")
					.append("    enabled: ").append(enabled.contains(provider)).append('\n')
					.append("    priority: ").append(provider.getPriority()).append('\n')
					.append("    entrypoints:");
			if (entrypoints) yaml.append("\n      - \"").append(provider.getEntrypoint()).append("\"\n");
			else yaml.append(" []\n");
		}

		return yaml.toString();
	}
}
