package me.whereareiam.identica.type.pipeline.journey;

/**
 * How a scenario picks among its options, each a provider method that can get the scenario done.
 */
public enum JourneyChoice {
	/**
	 * Identica picks: the provider the connection's selection ranks first, such as by hostname or a linked account,
	 * then by priority, and the provider's methods in their order. When an option cannot get the scenario done, the
	 * next one is tried.
	 */
	AUTOMATIC,

	/**
	 * The player picks among the options, unless the selection already settled the provider, such as by hostname, or
	 * only one option is left.
	 */
	PROMPTED
}
