package me.whereareiam.identica.feature.restriction.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.identity.actor.ConnectionIdentity;
import me.whereareiam.identica.feature.restriction.type.RestrictionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Input used to evaluate a provider restriction type.
 */
@Getter
@ToString
@Builder(toBuilder = true)
public class RestrictionEvaluationRequest {
	private final @NotNull RestrictionType type;
	private final @Nullable String providerId;
	private final @Nullable String providerSubject;
	private final @Nullable String providerUsername;
	private final @Nullable UUID connectionUniqueId;
	private final @Nullable String ip;
	private final @Nullable ConnectionIdentity.Origin origin;
}
