package me.whereareiam.identica.addon.dialogs.presentation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.addon.dialogs.model.LoadingScreenRule;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.model.checkpoint.CheckpointRequest;
import me.whereareiam.identica.model.provider.ProviderContext;
import me.whereareiam.identica.pipeline.ScenarioContext;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Picks the rule that decides: the first whose conditions all match the attributes of a message and its player.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RuleMatcher {
	/**
	 * Attribute of what the message asks for.
	 */
	static final String PROMPT = "prompt";
	/**
	 * Attribute of the message's ID.
	 */
	static final String MESSAGE = "message";
	/**
	 * Attribute of the player's pending scenario.
	 */
	static final String SCENARIO = "scenario";
	/**
	 * Attribute of the hostname the player connected through.
	 */
	static final String HOST = "host";

	/**
	 * Tells whether the loading-screen rule asks a connection on the loading screen. Its conditions see what is known
	 * about the connection when its first step waits: the {@code scenario}, the {@code provider}, the {@code host} the
	 * player connected through and the {@code server} they intend to reach.
	 */
	static boolean onLoadingScreen(@NotNull LoadingScreenRule rule, @NotNull CheckpointRequest request) {
		if (!rule.isEnabled()) return false;

		ScenarioContext context = request.getContext();
		Map<String, String> attributes = new HashMap<>();
		attributes.put(SCENARIO, request.getPipelineType().name());
		ProviderContext provider = context.getProvider();
		if (provider != null && provider.getProviderId() != null) attributes.put("provider", provider.getProviderId());
		ConnectionIdentity.Origin origin = context.getIdentity().getOrigin();
		if (origin != null) attributes.put(HOST, origin.getHost());
		if (context.getIntendedServer() != null) attributes.put("server", context.getIntendedServer());

		return matches(rule.getWhen(), attributes);
	}

	static @NotNull Optional<DialogRule> first(@NotNull List<DialogRule> rules, @NotNull Map<String, String> attributes) {
		return rules.stream()
				.filter(rule -> matches(rule.getWhen(), attributes))
				.findFirst();
	}

	private static boolean matches(Map<String, String> conditions, Map<String, String> attributes) {
		for (Map.Entry<String, String> condition : conditions.entrySet()) {
			String attribute = attributes.get(condition.getKey());
			if (attribute == null || !attribute.equalsIgnoreCase(condition.getValue())) return false;
		}

		return true;
	}
}
