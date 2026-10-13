package me.whereareiam.identica.model.identity.username;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.type.provider.ProviderClaimOrigin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * An account arriving through a provider, with the username the provider gives it.
 */
@Getter
@ToString
@Builder(toBuilder = true)
public class UsernameArrival {
	/**
	 * The account, which may not be stored yet, or {@code null} when a new account has no id yet.
	 */
	private final @Nullable UUID accountUniqueId;

	/**
	 * Id of the provider the account arrives through.
	 */
	private final @NotNull String providerId;

	/**
	 * The username the provider gives the player now.
	 */
	private final @NotNull String username;

	/**
	 * The username the provider gave the account when Identica saw it last, or {@code null} when the provider is new
	 * to the account.
	 */
	private final @Nullable String knownUsername;

	/**
	 * Whether the account takes its name from the provider for the first time: a new account, or a provider newly
	 * linked to it as the one its name follows.
	 */
	private final boolean firstJoin;

	/**
	 * Why the provider was picked for the connection, or {@code null} when not known.
	 */
	private final @Nullable ProviderClaimOrigin arrivedVia;
}
