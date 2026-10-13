package me.whereareiam.identica.model.config;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import me.whereareiam.identica.model.identity.username.UsernameConflictRule;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The username conflict rules, kept in {@code identity/username/conflicts}.
 */
@Getter
@Setter
@ToString
public class UsernameConflicts {
	/**
	 * The settings of every registered outcome, keyed by outcome id. Each outcome's defaults are written here once it
	 * is registered.
	 */
	private @NotNull Map<String, ObjectNode> outcomes = new LinkedHashMap<>();

	/**
	 * Settings that change an outcome for the provider of the account the outcome acts on, keyed by provider id and
	 * then outcome id; each entry states only what it changes.
	 */
	private @NotNull Map<String, Map<String, ObjectNode>> providers = new LinkedHashMap<>();

	/**
	 * The rules, in the order ties between equally specific rules are broken.
	 */
	private @NotNull List<UsernameConflictRule> rules = new ArrayList<>();
}
