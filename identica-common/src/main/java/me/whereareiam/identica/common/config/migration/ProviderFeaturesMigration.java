package me.whereareiam.identica.common.config.migration;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Moves what a provider entry of {@code providers/providers} says about recognition, verification, its join
 * restriction and its sessions to where an entry says it now: {@code features.recognition},
 * {@code features.verification}, {@code features.restriction.join} and {@code session}.
 * <ul>
 *     <li>2.2.1 and the May 2026 development builds: {@code overrides.allowRecognitionOnUntrustedIp}, or
 *     {@code overrides.recognition.eligibility.allowOnUntrustedIp}, becomes
 *     {@code features.recognition.allowOnUntrustedIps}; {@code overrides.recognition.enabled} and
 *     {@code .signals} keep their names; {@code overrides.sessionConcurrencyPolicy} becomes
 *     {@code session.concurrencyPolicy}; {@code verification} becomes {@code features.verification} and the May
 *     builds' {@code joinRestriction} becomes {@code features.restriction.join}.</li>
 *     <li>The June 8 builds: {@code session.recognition} becomes {@code features.recognition}, and
 *     {@code restriction} and {@code verification} move under {@code features}.</li>
 *     <li>The later June builds: {@code capabilities} merges into {@code features}.</li>
 * </ul>
 * 2.2.1 wrote its recognition and verification settings into the entries of {@code credential} and
 * {@code premium} whether or not an operator changed them. Of those, a value that is still 2.2.1's default is
 * dropped, so the entry takes the current defaults, which behave the same; a value an operator changed is moved.
 * Every other value is moved as it is.
 */
@RequiredArgsConstructor
final class ProviderFeaturesMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 6;
	static final String NAME = "provider-features";

	private static final String PROVIDERS = "providers/providers";
	/**
	 * What the core of 2.2.1 wrote into its provider entries by default, frozen as a historical record that only
	 * this migration reads: the ids of the entries 2.2.1 shipped in {@code providers/providers}, and below, the
	 * recognition and verification values it wrote into both. It is the one place the core names a provider, and
	 * gives no provider any behaviour; it goes once upgrades from 2.2.1 are no longer supported.
	 */
	private static final Set<String> RELEASE_221_PROVIDERS = Set.of("credential", "premium");

	/**
	 * The recognition and verification values the core of 2.2.1 wrote into the entries of
	 * {@link #RELEASE_221_PROVIDERS} whether or not an operator changed them, in the places this migration moves
	 * them to. Historical, like those ids; a value equal to it is dropped so the current defaults apply.
	 */
	static final ObjectNode RELEASE_221_DEFAULTS = parse("""
			{
			  "recognition": {"allowOnUntrustedIps": false, "signals": []},
			  "verification": {
			    "enabled": true,
			    "required": false,
			    "unavailableSelectionPolicy": "KEEP_LOCKED",
			    "methods": [{"id": "totp", "enabled": true, "priority": 100, "unavailableSelectionPolicy": "KEEP_LOCKED"}]
			  }
			}
			""");

	/** Extension of the documents, with its dot. */
	private final @NotNull String extension;

	@Override
	public void apply(@NotNull ConfigContext files) {
		String providers = PROVIDERS + extension;
		if (!files.exists(providers) || !(files.document(providers).get("providers") instanceof ArrayNode entries)) return;

		for (JsonNode entry : entries) {
			if (entry instanceof ObjectNode provider) migrate(provider);
		}
	}

	private static void migrate(ObjectNode entry) {
		boolean release221 = RELEASE_221_PROVIDERS.contains(entry.path("id").asText());

		if (entry.remove("overrides") instanceof ObjectNode overrides) {
			ObjectNode recognition = overrides.path("recognition") instanceof ObjectNode object ? object : null;
			JsonNode untrusted = overrides.has("allowRecognitionOnUntrustedIp")
					? overrides.get("allowRecognitionOnUntrustedIp")
					: recognition != null ? recognition.path("eligibility").get("allowOnUntrustedIp") : null;
			place(entry, "recognition", "allowOnUntrustedIps", untrusted, release221);
			if (recognition != null) {
				place(entry, "recognition", "enabled", recognition.get("enabled"), release221);
				place(entry, "recognition", "signals", recognition.get("signals"), release221);
			}

			JsonNode concurrency = overrides.get("sessionConcurrencyPolicy");
			if (concurrency != null && !concurrency.isNull() && !entry.path("session").has("concurrencyPolicy"))
				entry.withObject("/session").set("concurrencyPolicy", concurrency);
		}

		if (entry.remove("verification") instanceof ObjectNode verification) {
			for (String field : ObjectFields.names(verification))
				place(entry, "verification", field, verification.get(field), release221);
		}

		if (entry.remove("joinRestriction") instanceof ObjectNode join) mergeFeature(entry, "restriction", wrap("join", join));
		if (entry.remove("restriction") instanceof ObjectNode restriction) mergeFeature(entry, "restriction", restriction);

		if (entry.path("session").path("recognition") instanceof ObjectNode recognition) {
			((ObjectNode) entry.get("session")).remove("recognition");
			mergeFeature(entry, "recognition", recognition);
		}

		if (entry.remove("capabilities") instanceof ObjectNode capabilities) {
			for (String feature : ObjectFields.names(capabilities)) {
				if (capabilities.get(feature) instanceof ObjectNode settings) mergeFeature(entry, feature, settings);
			}
		}

		removeIfEmpty(entry, "session");
		removeIfEmpty(entry, "features");
	}

	/**
	 * Sets a feature's value on the entry, unless the entry has one or the value is 2.2.1's default for an entry
	 * 2.2.1 wrote it into.
	 */
	private static void place(ObjectNode entry, String feature, String field, @Nullable JsonNode value, boolean release221) {
		if (value == null || value.isNull()) return;
		if (release221 && value.equals(RELEASE_221_DEFAULTS.path(feature).get(field))) return;

		ObjectNode settings = entry.withObject("/features").withObject("/" + feature);
		if (!settings.has(field)) settings.set(field, value);
	}

	/** Merges settings into a feature's section, keeping the values the section has. */
	private static void mergeFeature(ObjectNode entry, String feature, ObjectNode settings) {
		merge(entry.withObject("/features").withObject(JsonPointer.empty().appendProperty(feature).toString()), settings);
	}

	private static void merge(ObjectNode target, ObjectNode source) {
		for (String field : ObjectFields.names(source)) {
			JsonNode value = source.get(field);
			if (value instanceof ObjectNode object && target.get(field) instanceof ObjectNode existing)
				merge(existing, object);
			else if (!target.has(field))
				target.set(field, value);
		}
	}

	private static ObjectNode wrap(String field, ObjectNode value) {
		ObjectNode wrapper = value.objectNode();
		wrapper.set(field, value);
		return wrapper;
	}

	private static void removeIfEmpty(ObjectNode entry, String field) {
		if (entry.get(field) instanceof ObjectNode object && object.isEmpty()) entry.remove(field);
	}

	private static ObjectNode parse(String json) {
		try {
			return (ObjectNode) new ObjectMapper().readTree(json);
		} catch (JsonProcessingException failure) {
			throw new IllegalStateException("Invalid defaults of 2.2.1", failure);
		}
	}
}
