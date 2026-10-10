package me.whereareiam.identica.platform.bungeecord.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.bungeecord.BungeeComponentSerializer;
import net.md_5.bungee.api.chat.BaseComponent;
import org.jetbrains.annotations.NotNull;

public final class BaseComponentMapper {
	public static BaseComponent @NotNull [] map(@NotNull Component component) {
		return BungeeComponentSerializer.get().serialize(component);
	}
}
