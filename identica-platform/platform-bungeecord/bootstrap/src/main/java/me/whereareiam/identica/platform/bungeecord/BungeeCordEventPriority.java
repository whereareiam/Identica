package me.whereareiam.identica.platform.bungeecord;

import me.whereareiam.identica.type.event.EventPriority;

public final class BungeeCordEventPriority {
	public static byte of(EventPriority priority) {
		return switch (priority) {
			case LOWEST -> net.md_5.bungee.event.EventPriority.LOWEST;
			case LOW -> net.md_5.bungee.event.EventPriority.LOW;
			case NORMAL -> net.md_5.bungee.event.EventPriority.NORMAL;
			case HIGH -> net.md_5.bungee.event.EventPriority.HIGH;
			case HIGHEST -> net.md_5.bungee.event.EventPriority.HIGHEST;
		};
	}
}
