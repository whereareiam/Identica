package me.whereareiam.identica.type.identity.username;

/**
 * What happens when a rule renames a holder who is online at that moment.
 */
public enum UsernameHolderOnline {
	/**
	 * The holder keeps playing under the name they joined with until they leave; the arriving player plays this
	 * session under a temporary name and gets the name from the next join on.
	 */
	KEEP_PLAYING,

	/**
	 * The holder is disconnected. The proxy names the arriving player while the holder is still connected, so the
	 * arriving player plays this session under a temporary name and gets the name from the next join on.
	 */
	KICK_HOLDER
}
