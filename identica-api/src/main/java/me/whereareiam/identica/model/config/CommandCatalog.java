package me.whereareiam.identica.model.config;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.model.CommandDefinition;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * The command definitions of one {@code commands} document, keyed by command id.
 * <p>
 * Core, providers and features each keep such a document for the commands they register. Core's
 * document is a {@link Commands}, which adds the behavior settings of the core commands.
 */
@Getter
@Setter
@ToString
public class CommandCatalog {
	private @NotNull Map<String, CommandDefinition> commands = new HashMap<>();
}
