package me.whereareiam.identica.testing.environment;

import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;
import me.whereareiam.anvil.integration.junit.AnvilScenarioFactory;
import me.whereareiam.anvil.integration.junit.ScenarioResources;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Builds the network an {@link Identica} declaration describes.
 */
public final class IdenticaScenarios implements AnvilScenarioFactory<Identica> {
	@Override
	public @NotNull AnvilScenario create(@NotNull Identica identica, @NotNull ScenarioResources resources) {
		Set<Provider> enabled = EnumSet.noneOf(Provider.class);
		enabled.addAll(Arrays.asList(identica.providers()));

		// Each network has its own stand-in for Mojang; the test receives it to register premium accounts.
		YggdrasilMock yggdrasil = resources.own(YggdrasilMock.start());
		ProxyConfiguration proxy = new ProxyConfiguration(identica.mode(), identica.policy(), enabled, identica.entrypoints(),
				identica.autoSelectSingleProvider(), identica.step(), identica.complete(), yggdrasil);
		for (Class<? extends Consumer<ProxyConfiguration>> change : identica.configure())
			instantiate(change).accept(proxy);

		return IdenticaNetwork.velocity(name(identica, enabled), Map.of(IdenticaNetwork.PROXY, proxy.files()), yggdrasil.sessionServer());
	}

	private static Consumer<ProxyConfiguration> instantiate(Class<? extends Consumer<ProxyConfiguration>> change) {
		try {
			var constructor = change.getDeclaredConstructor();
			constructor.setAccessible(true);
			return constructor.newInstance();
		} catch (ReflectiveOperationException failure) {
			throw new IllegalStateException(change.getName() + " needs a constructor without parameters", failure);
		}
	}

	private static String name(Identica identica, Set<Provider> enabled) {
		String providers = enabled.stream().map(Provider::getId).collect(Collectors.joining("+"));
		return String.join("-", "identica", identica.mode().name(), identica.policy().name(), providers,
				identica.entrypoints() ? "entrypoints" : "direct",
				identica.autoSelectSingleProvider() ? "autoselect" : "choice",
				routing(identica.step(), identica.complete()),
				Arrays.stream(identica.configure()).map(Class::getSimpleName).collect(Collectors.joining("+")))
				.replaceAll("-+$", "").toLowerCase(Locale.ROOT);
	}

	/**
	 * Names the routing targets of a network, so networks routed differently do not share a workspace.
	 */
	static String routing(String step, String complete) {
		return String.join("-", "step", step.isBlank() ? "none" : step, "complete", complete.isBlank() ? "none" : complete);
	}
}
