package me.whereareiam.identica.type.provider;

/**
 * What a provider guarantees about the identities it vouches for. A guarantee is fixed by the provider's
 * implementation: it is not an operator switch and does not stand for an optional feature integration. Username
 * conflict rules match on the guarantees of the providers on both sides.
 */
public enum ProviderGuarantee {
	/**
	 * The provider owns its players' usernames: no two of its players hold the same name at once, and it verifies
	 * that a player owns the name they join with. Identica follows that name for the account the provider is the
	 * primary link of, unless an administrator set the account's name by hand.
	 */
	OWNED_USERNAME
}
