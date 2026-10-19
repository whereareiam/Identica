package me.whereareiam.identica.addon.dialogs.model;

import lombok.Builder;
import lombok.Singular;
import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Whether players are asked on the loading screen, before they join any server, instead of on the step's server.
 * The addon decides it once per connection, when its first step waits, so the conditions name what is known about
 * the connection then: {@code scenario}, {@code provider}, {@code host} and {@code server}.
 */
@Value
@Builder
public class LoadingScreenRule {
	/**
	 * Whether the loading screen is used at all.
	 */
	boolean enabled;
	/**
	 * Conditions by attribute name; each matches when the connection's attribute has its value, ignoring case. A
	 * condition on another attribute never matches.
	 */
	@NotNull @Singular("when") Map<String, String> when;
}
