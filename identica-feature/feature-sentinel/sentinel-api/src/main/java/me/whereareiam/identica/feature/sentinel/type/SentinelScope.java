package me.whereareiam.identica.feature.sentinel.type;

/**
 * Points of a connection where a sentinel can be enforced.
 */
public enum SentinelScope {
	/**
	 * A connection arrives and starts or continues a login.
	 */
	PROCESS,

	/**
	 * A connection resumes a login it left.
	 */
	RESUME,

	/**
	 * A connection moves on to the next step of its login.
	 */
	ADVANCE,

	/**
	 * A connection is about to register an account through the provider it chose, before it is asked for anything.
	 */
	REGISTRATION
}
