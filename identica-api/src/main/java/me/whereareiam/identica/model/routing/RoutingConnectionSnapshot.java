package me.whereareiam.identica.model.routing;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Live platform routing context for one connection at the moment a routing attempt is evaluated.
 */
@Getter
@RequiredArgsConstructor
public class RoutingConnectionSnapshot {
	private final @NotNull UUID connectionUniqueId;
	private final @Nullable String username;
	private final @Nullable String currentServer;
}
