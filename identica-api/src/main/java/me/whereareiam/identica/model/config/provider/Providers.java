package me.whereareiam.identica.model.config.provider;

import lombok.Getter;
import lombok.AccessLevel;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.configura.ConfigDocument;
import me.whereareiam.configura.annotation.PreserveUnknownFields;
import me.whereareiam.configura.annotation.merge.Merge;
import me.whereareiam.configura.annotation.merge.MergeList;
import me.whereareiam.configura.feature.extension.api.annotation.ExtendableDocument;
import me.whereareiam.configura.merge.strategy.DeclaredObjectDefaults;
import me.whereareiam.configura.type.merge.tree.list.ListMode;
import me.whereareiam.configura.type.merge.tree.list.ListPresence;
import me.whereareiam.configura.type.merge.tree.list.ListUnknownEntries;
import me.whereareiam.identica.type.session.SessionConcurrencyPolicy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Provider configuration settings.
 */
@Getter
@Setter
@ToString
public class Providers extends ConfigDocument {
	private @NotNull Behavior behavior = new Behavior();
	@Merge
	@MergeList(
			mode = ListMode.KEYED,
			key = "id",
			presence = ListPresence.DECLARED_ONLY,
			unknownEntries = ListUnknownEntries.ALLOW
	)
	private @NotNull List<ProviderEntry> providers = new ArrayList<>();

	/**
	 * Shared provider runtime behavior settings.
	 */
	@Getter
	@Setter
	@ToString
	public static class Behavior {
		/**
		 * Time-to-live for provider attempt markers.
		 */
		private @NotNull Duration attemptTtl;

		/**
		 * Returns attempt TTL in milliseconds with validation.
		 *
		 * @return attempt TTL in milliseconds
		 */
		public long attemptTtlMillis() {
			if (attemptTtl.isZero() || attemptTtl.isNegative()) {
				throw new IllegalStateException("providers.behavior.attemptTtl must be positive");
			}

			return attemptTtl.toMillis();
		}

	}

	/**
	 * Provider definition entry.
	 */
	@Getter
	@Setter
	@ToString
	@ExtendableDocument
	@PreserveUnknownFields
	public static class ProviderEntry {
		private @NotNull String id;
		/**
		 * Optional user-facing label for this provider.
		 * Falls back to the provider descriptor name and then the raw id.
		 */
		private @Nullable String displayName;
		private boolean enabled;
		private int priority;
		/**
		 * Provider-specific session settings.
		 */
		@Merge(DeclaredObjectDefaults.class)
		@ExtendableDocument
		private @Nullable Session session;
		/**
		 * Feature-specific provider settings.
		 */
		@Merge(DeclaredObjectDefaults.class)
		@ExtendableDocument
		private @Nullable Features features;
		/**
		 * Hostnames (optionally with port) that map to this provider.
		 * Entries must use the format {@code host} or {@code host:port}.
		 */
		private @NotNull List<String> entrypoints = new ArrayList<>();

		/**
		 * Provider-specific session behavior settings.
		 */
		@Getter
		@Setter
		@ToString
		@ExtendableDocument
		public static class Session {
			private @Nullable SessionConcurrencyPolicy concurrencyPolicy;
		}

		/**
		 * Feature-specific provider settings.
		 */
		@Getter
		@Setter
		@ToString
		@ExtendableDocument
		@PreserveUnknownFields
		public static class Features {
			@Getter(AccessLevel.NONE)
			@Setter(AccessLevel.NONE)
			@JsonIgnore
			private final Map<String, JsonNode> entries = new LinkedHashMap<>();

			/**
			 * Preserves feature-owned settings while core reads their enable switches.
			 *
			 * @param id feature settings key
			 * @param settings opaque configuration owned by the feature
			 */
			@JsonAnySetter
			public void put(@NotNull String id, @NotNull JsonNode settings) {
				entries.put(id, settings);
			}

			/**
			 * Returns feature-owned settings for document serialization.
			 *
			 * @return feature settings keyed by their configuration names
			 */
			@JsonAnyGetter
			public @NotNull Map<String, JsonNode> entries() {
				return java.util.Collections.unmodifiableMap(entries);
			}

			/**
			 * Reads an explicit enable switch without interpreting feature policy.
			 * Missing values inherit the feature's own defaults.
			 *
			 * @param path dot-separated feature configuration path
			 * @return explicit switch, or null when the feature should inherit its defaults
			 */
			public @Nullable Boolean enabledOverride(@NotNull String path) {
				String[] segments = path.split("\\.");
				JsonNode node = entries.get(segments[0]);
				for (int i = 0; node != null; i++) {
					if (node.has("enabled") && !node.path("enabled").asBoolean(true)) return false;
					if (i + 1 == segments.length)
						return node.has("enabled") ? node.path("enabled").asBoolean(true) : null;
					node = node.get(segments[i + 1]);
				}
				return null;
			}
		}
	}
}
