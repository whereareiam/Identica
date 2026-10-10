package me.whereareiam.identica.platform.velocity.mapper;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.identity.IdentityService;
import me.whereareiam.identica.identity.actor.Identity;
import me.whereareiam.identica.platform.velocity.actor.VelocityCommandConsole;
import me.whereareiam.identica.platform.velocity.actor.VelocityCommandPlayer;
import me.whereareiam.keystone.Actor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.incendo.cloud.SenderMapper;

@Singleton
@RequiredArgsConstructor(onConstructor = @__(@Inject))
public class CommandSourceMapper implements SenderMapper<CommandSource, Actor> {
	private final IdentityService identityService;

	@Override
	public @NonNull Actor map(@NonNull CommandSource source) {
		if (source instanceof ConsoleCommandSource console)
			return new VelocityCommandConsole(console);

		if (source instanceof Player player) {
			Identity identity = identityService.findByConnectionUniqueId(player.getUniqueId()).orElse(null);
			if (identity != null) return identity;

			VelocityCommandPlayer created = new VelocityCommandPlayer(player);
			identityService.attach(player.getUniqueId(), null, created);
			return created;
		}

		throw new UnsupportedOperationException("Unsupported command source type: " + source.getClass().getName());
	}

	@Override
	public @NonNull CommandSource reverse(@NonNull Actor actor) {
		if (actor instanceof VelocityCommandPlayer player)
			return new PermissionAwareCommandSource(player.getSource());

		if (actor instanceof VelocityCommandConsole console)
			return new PermissionAwareCommandSource(console.getSource());

		throw new UnsupportedOperationException("Cannot reverse map Actor to CommandSource");
	}
}
