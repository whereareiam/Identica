package me.whereareiam.identica.platform.bungeecord.mapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.platform.bungeecord.actor.BungeeCordCommandConsole;
import me.whereareiam.identica.platform.bungeecord.actor.BungeeCordCommandPlayer;
import me.whereareiam.keystone.Actor;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import org.incendo.cloud.SenderMapper;
import org.jetbrains.annotations.NotNull;

@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class CommandSourceMapper implements SenderMapper<CommandSender, Actor> {
	private final IdentityService identityService;
	private final BungeeAudiences audiences;

	@Override
	public @NotNull Actor map(@NotNull CommandSender source) {
		Audience audience = audiences.sender(source);
		if (source instanceof ProxiedPlayer player) {
			Identity identity = identityService.find(player.getUniqueId()).orElse(null);
			if (identity != null) return identity;

			BungeeCordCommandPlayer created = new BungeeCordCommandPlayer(player, audience);
			identityService.attach(created);
			return created;
		}

		return new BungeeCordCommandConsole(source, audience);
	}

	@Override
	public @NotNull CommandSender reverse(@NotNull Actor actor) {
		if (actor instanceof BungeeCordCommandPlayer player)
			return player.getSource();

		if (actor instanceof BungeeCordCommandConsole console)
			return console.getSource();

		throw new UnsupportedOperationException("Cannot reverse map Actor to CommandSender");
	}
}
