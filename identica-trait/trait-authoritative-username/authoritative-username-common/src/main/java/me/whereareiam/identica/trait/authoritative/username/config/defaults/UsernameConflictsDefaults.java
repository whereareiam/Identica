package me.whereareiam.identica.trait.authoritative.username.config.defaults;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.DefaultsProvider;
import me.whereareiam.identica.model.config.provider.Conflicts;

import java.util.List;

@Singleton
public class UsernameConflictsDefaults implements DefaultsProvider<Conflicts> {
	@Override
	public Conflicts supply(Conflicts config) {
		Conflicts.ConflictRules usernameRules = new Conflicts.ConflictRules();
		Conflicts.ConflictRules.ConflictRule defaultRule = new Conflicts.ConflictRules.ConflictRule();
		defaultRule.setResolvers(List.of(formatResolver("{username}*")));
		usernameRules.setDefaultRule(defaultRule);

		ObjectNode when = JsonNodeFactory.instance.objectNode();
		when.putArray("providers")
				.add("premium")
				.add("credential");

		Conflicts.ConflictRules.ConflictRule premiumVsCredential = new Conflicts.ConflictRules.ConflictRule();
		premiumVsCredential.setWhen(when);
		premiumVsCredential.setResolvers(List.of(formatResolver("{username}_{incomingProvider}")));
		usernameRules.setCases(List.of(premiumVsCredential));

		config.getRules().put("username", usernameRules);
		return config;
	}

	private Conflicts.ConflictRules.ConflictRule.ResolverEntry formatResolver(String pattern) {
		ObjectNode format = JsonNodeFactory.instance.objectNode();
		format.put("pattern", pattern);

		ObjectNode node = JsonNodeFactory.instance.objectNode();
		node.set("format", format);
		node.put("target", "joiner");
		Conflicts.ConflictRules.ConflictRule.ResolverEntry entry =
				new Conflicts.ConflictRules.ConflictRule.ResolverEntry();
		entry.setId("format_display");
		entry.setParameters(node);
		return entry;
	}
}
