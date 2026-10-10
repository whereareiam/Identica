package me.whereareiam.identica.platform.velocity.mapper;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.audience.ForwardingAudience;
import org.jetbrains.annotations.NotNull;

final class PermissionAwareCommandSource implements CommandSource, ForwardingAudience.Single {
	private final CommandSource delegate;

	PermissionAwareCommandSource(@NotNull CommandSource delegate) {
		this.delegate = delegate;
	}

	@Override
	public @NotNull Audience audience() {
		return delegate;
	}

	@Override
	public Tristate getPermissionValue(String permission) {
		return delegate.getPermissionValue(permission);
	}

	@Override
	public boolean hasPermission(String permission) {
		if (permission == null || permission.isBlank()) return true;
		return delegate.hasPermission(permission);
	}
}
