package me.whereareiam.identica.type;

/**
 * Enumeration representing different Minecraft server platform types.
 * This enum provides methods to detect and compare various server implementations
 * such as BungeeCord and Velocity.
 */
@SuppressWarnings("unused")
public enum PlatformType {
	/**
	 * Represents the BungeeCord proxy server platform
	 */
	BUNGEECORD,
	/**
	 * Represents the Velocity proxy server platform
	 */
	VELOCITY,
	/**
	 * Represents an unknown or unsupported platform type
	 */
	UNKNOWN;

	/**
	 * Determines the current platform type by checking the presence of platform-specific classes.
	 *
	 * @return The detected {@link PlatformType} based on the current environment
	 */
	public static PlatformType getType() {
		if (isBungeeCord()) return BUNGEECORD;
		if (isVelocity()) return VELOCITY;

		return UNKNOWN;
	}

	/**
	 * Checks if the current platform is a proxy server.
	 *
	 * @return true if the platform is Velocity, false otherwise
	 */
	public static boolean isProxy() {
		return isBungeeCord() || isVelocity();
	}

	/**
	 * Checks if the platform is running BungeeCord.
	 *
	 * @return true if BungeeCord is detected, false otherwise
	 */
	private static boolean isBungeeCord() {
		return isClassPresent("net.md_5.bungee.api.plugin.Plugin");
	}

	/**
	 * Checks if the platform is running Velocity.
	 *
	 * @return true if Velocity is detected, false otherwise
	 */
	private static boolean isVelocity() {
		return isClassPresent("com.velocitypowered.api.plugin.Plugin");
	}

	/**
	 * Utility method to check if a specific class is present in the classpath.
	 *
	 * @param className The fully qualified name of the class to check
	 * @return true if the class is present, false otherwise
	 */
	private static boolean isClassPresent(String className) {
		try {
			Class.forName(className);
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
    }
}
