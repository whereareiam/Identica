package me.whereareiam.identica.platform.velocity.util;

import me.whereareiam.identica.type.event.EventPriority;

public class VelocityUtil {
	public static short of(EventPriority priority) {
		return switch (priority) {
			case LOWEST -> Short.MIN_VALUE;
			case LOW -> Short.MIN_VALUE / 2;
			case NORMAL -> 0;
			case HIGH -> Short.MAX_VALUE / 2;
			case HIGHEST -> Short.MAX_VALUE;
		};
	}
}
