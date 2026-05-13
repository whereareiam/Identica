package me.whereareiam.identica.platform.bungeecord.actor;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.whereareiam.keystone.Actor;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.md_5.bungee.api.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

@RequiredArgsConstructor
public class BungeeCordCommandConsole implements Actor {
	@Getter
    private final CommandSender source;
	private final Audience audience;

    @Override
	public @NotNull UUID getUniqueId() {
		return new UUID(0L, 0L);
	}

	@Override
	public @NotNull String getUsername() {
		return "Console";
	}

	@Override
	public void sendMessage(@NotNull Component message) {
		audience.sendMessage(message);
	}

	@Override
	public boolean hasPermission(@NotNull String permission) {
		if (permission.isBlank()) return true;
		return source.hasPermission(permission);
	}

	@Override
	public @NotNull Locale getLocale() {
		return Locale.ENGLISH;
	}

	@Override
	public @NotNull Audience getAudience() {
		return audience;
	}
}
