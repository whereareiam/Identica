package me.whereareiam.identica.model.conflict;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;
import me.whereareiam.identica.type.provider.ProviderGuarantee;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

/**
 * One side of a conflict: an account with the provider it stands on there. The arriving side is the account of the
 * joining player through the provider it joins with; the holding side is the account that has what the arriving
 * one wants, through its primary provider.
 */
@Getter
@ToString
@Builder(toBuilder = true)
public class ConflictParty {
	/**
	 * The account, or {@code null} for a new account that has no id yet.
	 */
	private final @Nullable UUID accountUniqueId;

	/**
	 * Id of the provider, or {@code null} when the account has no provider link.
	 */
	private final @Nullable String providerId;

	/**
	 * What the provider guarantees; empty when it guarantees nothing or there is no provider.
	 */
	@Singular
	private final @NotNull Set<ProviderGuarantee> guarantees;
}
