package me.whereareiam.identica.model.config.provider;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Conflict resolution configuration settings.
 */
@Getter
@Setter
@ToString
public class Conflicts {
	private @NotNull Map<String, ConflictRules> rules = new HashMap<>();

	/**
	 * Conflict rules for a single conflict key.
	 */
	@Getter
	@Setter
	@ToString
	public static class ConflictRules {
		@JsonProperty("default")
		private @NotNull ConflictRule defaultRule;
		private @NotNull List<ConflictRule> cases = new ArrayList<>();

		/**
		 * Conflict rule for a type-owned selector scope.
		 */
		@Getter
		@Setter
		@ToString
		public static class ConflictRule {
			private @NotNull JsonNode when = JsonNodeFactory.instance.objectNode();
			private boolean force;
			private @NotNull List<ResolverEntry> resolvers = new ArrayList<>();

			/**
			 * Resolver entry for conflict rules.
			 */
			@Getter
			@Setter
			@ToString
			public static class ResolverEntry {
				private @NotNull String id;
				private @NotNull JsonNode parameters = JsonNodeFactory.instance.objectNode();
			}
		}
	}
}
