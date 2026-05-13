package me.whereareiam.identica.common.config.defaults;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.MergeDefaultsProvider;
import me.whereareiam.identica.model.config.Providers;
import me.whereareiam.identica.type.verification.UnavailableSelectionPolicy;

import java.time.Duration;
import java.util.List;

@Singleton
public class ProvidersDefaults implements MergeDefaultsProvider<Providers> {
	@Override
	public Providers supply(Providers config) {
		Providers.ConflictRules usernameRules = new Providers.ConflictRules();
		Providers.ConflictRule defaultRule = new Providers.ConflictRule();
		defaultRule.setResolvers(List.of(formatResolver("{username}*")));
		usernameRules.setDefaultRule(defaultRule);

		Providers.ConflictRule premiumVsPassword = new Providers.ConflictRule();
		premiumVsPassword.setProviders(List.of("premium", "password"));
		premiumVsPassword.setResolvers(List.of(formatResolver("{username}_{incomingProvider}")));
		usernameRules.setPairs(List.of(premiumVsPassword));

		config.getConflicts().put("username", usernameRules);

		Providers.ProviderEntry password = new Providers.ProviderEntry();
		password.setId("password");
		password.setDisplayName("CR");
		password.setEnabled(true);
		password.setPriority(50);
		password.setEntrypoints(List.of("password.arcadeya.com"));
		password.setVerification(passwordVerification());

		Providers.ProviderEntry premium = new Providers.ProviderEntry();
		premium.setId("premium");
		premium.setDisplayName("PR");
		premium.setEnabled(true);
		premium.setPriority(100);
		premium.getOverrides().setSessionTtl(Duration.ofHours(12));
		premium.setEntrypoints(List.of("premium.arcadeya.com"));
		premium.setVerification(premiumVerification());

		config.setProviders(List.of(password, premium));
		return config;
	}

	private Providers.ResolverEntry formatResolver(String pattern) {
		ObjectNode format = JsonNodeFactory.instance.objectNode();
		format.put("pattern", pattern);

		ObjectNode node = JsonNodeFactory.instance.objectNode();
		node.set("format", format);
		node.put("target", "joiner");
		Providers.ResolverEntry entry = new Providers.ResolverEntry();
		entry.setId("format_display");
		entry.setParameters(node);
		return entry;
	}

	private Providers.Verification passwordVerification() {
		Providers.Verification verification = new Providers.Verification();
		verification.setEnabled(true);
		verification.setRequired(false);
		verification.setUnavailableSelectionPolicy(UnavailableSelectionPolicy.KEEP_LOCKED);

		Providers.Verification.MethodEntry totp = new Providers.Verification.MethodEntry();
		totp.setId("totp");
		totp.setEnabled(true);
		totp.setPriority(100);
		totp.setUnavailableSelectionPolicy(UnavailableSelectionPolicy.KEEP_LOCKED);
		verification.setMethods(List.of(totp));

		return verification;
	}

	private Providers.Verification premiumVerification() {
		Providers.Verification verification = new Providers.Verification();
		verification.setEnabled(true);
		verification.setRequired(false);
		verification.setUnavailableSelectionPolicy(UnavailableSelectionPolicy.KEEP_LOCKED);

		Providers.Verification.MethodEntry totp = new Providers.Verification.MethodEntry();
		totp.setId("totp");
		totp.setEnabled(true);
		totp.setPriority(100);
		totp.setUnavailableSelectionPolicy(UnavailableSelectionPolicy.KEEP_LOCKED);
		verification.setMethods(List.of(totp));

		return verification;
	}
}
