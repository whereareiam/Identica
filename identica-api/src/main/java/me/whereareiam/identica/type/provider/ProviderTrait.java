package me.whereareiam.identica.type.provider;

/**
 * Identity guarantees supplied by a provider implementation. Traits are not
 * operator switches and do not represent optional feature integrations.
 */
public enum ProviderTrait {
	/**
	 * The provider controls its verified username. Identica follows that name
	 * for the primary account link, subject to manual authority and conflict policy.
	 */
	AUTHORITATIVE_USERNAME
}
