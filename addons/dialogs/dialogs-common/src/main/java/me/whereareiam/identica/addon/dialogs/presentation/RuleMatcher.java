package me.whereareiam.identica.addon.dialogs.presentation;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.addon.dialogs.model.DialogRule;
import me.whereareiam.identica.addon.dialogs.model.LoadingScreenRule;
import me.whereareiam.identica.addon.dialogs.trigger.TriggerContext;
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
 * Finds the rule that decides a moment: the first that names the moment's trigger and whose conditions all match
 * its attributes, ignoring case. A condition on an attribute the trigger does not report never matches.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class RuleMatcher {
	static @NotNull Optional<DialogRule> first(@NotNull List<DialogRule> rules, @NotNull TriggerContext context) {
		return rules.stream()
				.filter(rule -> rule.getOn().equalsIgnoreCase(context.getTrigger()))
				.filter(rule -> matches(rule.getWhen(), context.getAttributes()))
				.findFirst();
	}

	/**
	 * Tells whether the loading-screen rule asks a connection on the loading screen. Its conditions see what is known
	 * about the connection when its first step waits: the {@code scenario}, the {@code provider}, the {@code host} the
	 * player connected through and the {@code server} they intend to reach.
	 */
	static boolean onLoadingScreen(@NotNull LoadingScreenRule rule, @NotNull CheckpointRequest request) {
		if (!rule.isEnabled()) return false;

		ScenarioContext context = request.getContext();
		Map<String, String> attributes = new HashMap<>();
		attributes.put("scenario", request.getPipelineType().name());
		ProviderContext provider = context.getProvider();
		if (provider != null && provider.getProviderId() != null) attributes.put("provider", provider.getProviderId());
		ConnectionIdentity.Origin origin = context.getIdentity().getOrigin();
		if (origin != null) attributes.put("host", origin.getHost());
		if (context.getIntendedServer() != null) attributes.put("server", context.getIntendedServer());

		return matches(rule.getWhen(), attributes);
	}

	private static boolean matches(Map<String, String> conditions, Map<String, String> attributes) {
		for (Map.Entry<String, String> condition : conditions.entrySet()) {
			String attribute = attributes.get(condition.getKey());
			if (attribute == null || !attribute.equalsIgnoreCase(condition.getValue())) return false;
		}

		return true;
	}
}
