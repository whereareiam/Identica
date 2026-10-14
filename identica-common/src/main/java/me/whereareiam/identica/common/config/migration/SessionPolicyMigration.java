package me.whereareiam.identica.common.config.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import me.whereareiam.strata.MigrationAction;
import me.whereareiam.strata.adapter.configura.ConfigContext;
import org.jetbrains.annotations.NotNull;

/**
 * Replaces the session concurrency policy {@code ALLOW_MULTIPLE}, which no longer exists since an account has one
 * session, with {@code REPLACE_EXISTING}: the newest login keeps the account, as with several sessions a new login
 * was let in. It is replaced in {@code sessions.concurrencyPolicy} of the settings document and in
 * {@code session.concurrencyPolicy} of each entry of {@code providers/providers}, where the earlier migrations put
 * the policy of 2.2.1 and the development builds. {@code REJECT_NEW} and {@code REPLACE_EXISTING} stay as they are.
 */
@RequiredArgsConstructor
final class SessionPolicyMigration implements MigrationAction<ConfigContext> {
	static final int VERSION = 9;
	static final String NAME = "single-session-policy";

	private static final String SETTINGS = "settings";
	private static final String PROVIDERS = "providers/providers";
	private static final String REMOVED = "ALLOW_MULTIPLE";
	private static final String REPLACEMENT = "REPLACE_EXISTING";

	/** Extension of the documents, with its dot. */
	private final @NotNull String extension;

	@Override
	public void apply(@NotNull ConfigContext files) {
		String settings = SETTINGS + extension;
		if (files.exists(settings) && isRemoved(files.document(settings).at("/sessions/concurrencyPolicy")))
			files.put(settings, "/sessions/concurrencyPolicy", TextNode.valueOf(REPLACEMENT));

		String providers = PROVIDERS + extension;
		if (!files.exists(providers) || !(files.document(providers).get("providers") instanceof ArrayNode entries)) return;

		for (JsonNode entry : entries) {
			if (entry.path("session") instanceof ObjectNode session && isRemoved(session.get("concurrencyPolicy")))
				session.put("concurrencyPolicy", REPLACEMENT);
		}
	}

	private static boolean isRemoved(JsonNode policy) {
		return policy != null && policy.isTextual() && policy.asText().trim().equalsIgnoreCase(REMOVED);
	}
}
