package me.whereareiam.identica.feature.restriction.join;

import me.whereareiam.identica.feature.restriction.type.RestrictionType;
import org.jetbrains.annotations.NotNull;

/**
 * Built-in join restriction type identifier.
 */
public final class JoinRestrictionType {
	public static final @NotNull RestrictionType TYPE = RestrictionType.of("join");
}
