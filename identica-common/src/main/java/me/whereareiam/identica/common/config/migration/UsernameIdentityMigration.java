package me.whereareiam.identica.common.config.migration;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Moves the username conflict rules and the username messages to their documents under {@code identity/username/}.
 * <p>
 * Conflict rules of 2.2.1 and of the May 2026 development builds are {@code conflicts} in {@code providers/providers};
 * those of the later builds up to June 13 are {@code rules} in {@code providers/conflicts}. The rules of
 * {@code username} go to {@code identity/username/conflicts} when the directory has no such document yet, and the
 * rules of other keys stay in {@code providers/conflicts}. A rule set written as {@code pairs}, each pair naming its
 * {@code providers}, becomes one written as {@code cases}, each case naming them under {@code when.providers}; the
 * default rule's empty {@code providers} becomes an empty {@code when}.
 * <p>
 * The username messages come from the messages document: a scenario's
 * {@code errors.identity.replicationMissing} under {@code connection} (2.2.1 and May) or {@code scenarios} (June)
 * becomes {@code pipeline.identity.synchronizationFailed}, which all scenarios share now, so the authentication
 * scenario's text is kept and the others are dropped; {@code conflictEntrypointRequired} of the authentication
 * scenario becomes {@code pipeline.policy.entrypointRequired}. The later June builds kept them in
 * {@code providers/capabilities/authoritative-username/messages}, which has the current layout.
 */
@RequiredArgsConstructor
final class UsernameIdentityMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 4;
	static final String NAME = "username-identity";

	private static final String CONFLICTS = "identity/username/conflicts";
	private static final String MESSAGES = "identity/username/messages";
	private static final String PROVIDER_CONFLICTS = "providers/conflicts";
	private static final String USERNAME = "username";
	private static final List<String> SCENARIOS = List.of("authentication", "registration", "migration");

	/** Extension of the documents, with its dot. */
	private final @NotNull String extension;

	@Override
	public void apply(@NotNull ConfigContext files) {
		moveConflicts(files);
		moveMessages(files);
	}

	private void moveConflicts(ConfigContext files) {
		String conflicts = CONFLICTS + extension;
		String providerConflicts = PROVIDER_CONFLICTS + extension;
		boolean usernameDocument = files.exists(conflicts);

		if (files.exists(providerConflicts) && files.document(providerConflicts).get("rules") instanceof ObjectNode rules) {
			for (String key : ObjectFields.names(rules)) {
				if (rules.get(key) instanceof ObjectNode ruleSet) rules.set(key, cases(ruleSet));
			}

			if (!usernameDocument) {
				files.moveIfAbsent(providerConflicts, "/rules/" + USERNAME, conflicts, "/rules/" + USERNAME);
				files.removeIfEmpty(providerConflicts, "/rules");
			}
		}

		String providers = "providers/providers" + extension;
		if (!(files.remove(providers, "/conflicts") instanceof ObjectNode earlier)) return;
		for (String key : ObjectFields.names(earlier)) {
			if (!(earlier.get(key) instanceof ObjectNode ruleSet)) continue;

			String target = key.equals(USERNAME) ? conflicts : providerConflicts;
			String pointer = JsonPointer.compile("/rules").appendProperty(key).toString();
			if (!files.exists(target) || files.document(target).at(pointer).isMissingNode())
				files.put(target, pointer, cases(ruleSet));
		}
	}

	/** Returns a rule set written with cases, converting one written with pairs. */
	private static ObjectNode cases(ObjectNode ruleSet) {
		if (!ruleSet.has("pairs")) return ruleSet;

		ObjectNode converted = ruleSet.deepCopy();
		JsonNode pairs = converted.remove("pairs");
		ArrayNode cases = converted.putArray("cases");
		for (JsonNode pair : pairs) {
			if (pair instanceof ObjectNode rule) cases.add(rule(rule));
		}
		if (converted.get("default") instanceof ObjectNode defaultRule) {
			ObjectNode rule = rule(defaultRule);
			rule.set("when", JsonNodeFactory.instance.objectNode());
			converted.set("default", rule);
		}

		return converted;
	}

	/** Moves the providers a rule names under {@code when}, keeping its other fields in their order. */
	private static ObjectNode rule(ObjectNode pair) {
		ObjectNode rule = JsonNodeFactory.instance.objectNode();
		ObjectNode when = rule.putObject("when");
		JsonNode providers = pair.get("providers");
		if (providers != null) when.set("providers", providers);
		for (String field : ObjectFields.names(pair)) {
			if (!field.equals("providers")) rule.set(field, pair.get(field));
		}

		return rule;
	}

	private void moveMessages(ConfigContext files) {
		String messages = "messages" + extension;
		String username = MESSAGES + extension;

		files.moveFile("providers/capabilities/authoritative-username/messages" + extension, username);
		files.deleteDirectoryIfEmpty("providers/capabilities/authoritative-username");
		files.deleteDirectoryIfEmpty("providers/capabilities");

		for (String section : List.of("/connection/", "/scenarios/")) {
			files.moveIfAbsent(messages, section + "authentication/errors/identity/replicationMissing", username, "/pipeline/identity/synchronizationFailed");
			files.moveIfAbsent(messages, section + "authentication/conflictEntrypointRequired", username, "/pipeline/policy/entrypointRequired");
			for (String scenario : SCENARIOS) {
				files.remove(messages, section + scenario + "/errors/identity/replicationMissing");
				files.removeIfEmpty(messages, section + scenario + "/errors/identity");
				files.removeIfEmpty(messages, section + scenario);
			}
		}
	}
}
