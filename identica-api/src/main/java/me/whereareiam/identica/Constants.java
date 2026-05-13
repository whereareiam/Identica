package me.whereareiam.identica;

/**
 * Shared Identica constants.
 */
public final class Constants {
	public static final String NAME = BuildConfig.NAME;
	public static final String VERSION = BuildConfig.VERSION;

	/**
	 * bStats identifiers used by platform integrations.
	 */
	public static final class BStats {
		public static final int BUNGEECORD_ID = 31292;
		public static final int VELOCITY_ID = 30854;
	}

	/**
	 * Version constants for runtime dependencies.
	 */
	public static final class Dependency {
		public static final String GUICE = BuildConfig.GUICE;
		public static final String CONFIGURA = BuildConfig.CONFIGURA;
		public static final String COMMANDANT = BuildConfig.COMMANDANT;
		public static final String KEYSTONE = BuildConfig.KEYSTONE;
		public static final String DIALECTICA = BuildConfig.DIALECTICA;

		public static final String CLOUD_CORE = BuildConfig.CLOUD_CORE;
		public static final String CLOUD_ANNOTATIONS = BuildConfig.CLOUD_CORE;
		public static final String CLOUD_COOLDOWN = BuildConfig.CLOUD_COOLDOWN;
		public static final String CLOUD_BUNGEE = BuildConfig.CLOUD_BUNGEE;
		public static final String CLOUD_VELOCITY = BuildConfig.CLOUD_VELOCITY;
		public static final String CLOUD_MINECRAFT_EXTRAS = BuildConfig.CLOUD_MINECRAFT_EXTRAS;

		public static final String JDBI = BuildConfig.JDBI;
		public static final String HIKARI = BuildConfig.HIKARICP;
		public static final String POSTGRES = BuildConfig.POSTGRESQL;
		public static final String MARIADB = BuildConfig.MARIADB;
		public static final String SQLITE = BuildConfig.SQLITE;
		public static final String H2 = BuildConfig.H2;

		public static final String JEDIS = BuildConfig.JEDIS;
	}
}
