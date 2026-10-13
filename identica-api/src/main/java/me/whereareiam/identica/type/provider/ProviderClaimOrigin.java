package me.whereareiam.identica.type.provider;

import me.whereareiam.identica.provider.selection.ProviderSelector;

/**
 * Why a provider claims a connection.
 *
 * <p>Several claims may name different providers for the same connection. Which one wins is decided by the
 * engine's ranking, never by the order of these constants; {@link ProviderSelector#isAtLeast} compares two origins
 * by that ranking.</p>
 */
public enum ProviderClaimOrigin {
	/**
	 * A migration of the connection's account to this provider is pending.
	 */
	MIGRATION,

	/**
	 * The provider's subject for the connection is already linked to an account. Identica's own source only counts a
	 * subject the provider verified: a subject derived from the username proves no more than a name match.
	 */
	ACCOUNT_LINK,

	/**
	 * The connection arrived through one of the provider's entrypoint hostnames.
	 */
	ENTRYPOINT,

	/**
	 * The provider verified, for this connection, that the client owns its subject, such as through an online login.
	 * A verified subject that is already linked to an account also makes an {@link #ACCOUNT_LINK} claim, which ranks
	 * higher.
	 */
	VERIFIED_SUBJECT,

	/**
	 * The player chose the provider, such as in the interactive provider choice.
	 */
	PLAYER_CHOICE,

	/**
	 * An account with the connection's username uses the provider. Nothing about the client is proven.
	 */
	NAME_MATCH,

	/**
	 * The provider resolves a subject for the connection, without proof and without a linked account.
	 */
	DISCOVERY
}
