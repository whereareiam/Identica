package me.whereareiam.identica.conflict.resolver.typed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility for binding conflict resolver parameters.
 */
public final class ConflictParameters {
	/**
	 * Bind a configuration node to the given config type.
	 *
	 * <pre>{@code
	 * public final class FormatConfig {
	 *     public String format;
	 * }
	 *
	 * FormatConfig config = ConflictParameters.bind(params, FormatConfig.class);
	 * }</pre>
	 *
	 * @param node parameter node
	 * @param type config type
	 * @param <T>  config type
	 * @return bound config instance
	 */
	public static <T> @NotNull T bind(
			@Nullable JsonNode node,
			@NotNull Class<T> type
	) {
		JsonNode safe = node != null ? node : JsonNodeFactory.instance.objectNode();
		Configura config = Config.configured().toBuilder().format(Format.YAML).build();
		return config.read(config.writeNodeBytes(safe), type);
	}
}
