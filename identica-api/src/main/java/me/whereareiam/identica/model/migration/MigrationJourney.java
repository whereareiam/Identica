package me.whereareiam.identica.model.migration;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A migration journey to start for a connection: the account that moves to another provider, the player it is
 * started for and who asked for it. The player meets the journey when they connect again.
 *
 * <pre>{@code
 * connectionCoordinator.startMigrationJourney(MigrationJourney.builder()
 *         .connectionUniqueId(player.getConnectionUniqueId())
 *         .accountUniqueId(player.getAccountUniqueId())
 *         .username(player.getUsername())
 *         .targetProviderId("premium")
 *         .requestedAt(System.currentTimeMillis())
 *         .initiator(MigrationInitiator.USER)
 *         .build());
 * }</pre>
 */
@Getter
@Builder
@ToString
public class MigrationJourney {
	/**
	 * Connection the journey belongs to.
	 */
	private final @NotNull UUID connectionUniqueId;
	/**
	 * Account that migrates.
	 */
	private final @NotNull UUID accountUniqueId;
	/**
	 * Username of the player, or {@code null} when it is not known.
	 */
	private final @Nullable String username;
	/**
	 * Address the player connected from, or {@code null} when it is not known.
	 */
	private final @Nullable String ip;
	/**
	 * Provider the account migrates to.
	 */
	private final @NotNull String targetProviderId;
	/**
	 * When the migration was asked for, in milliseconds since the epoch.
	 */
	private final long requestedAt;
	/**
	 * Who asked for the migration, or {@code null} when it is not known.
	 */
	private final @Nullable MigrationInitiator initiator;
	/**
	 * Unique id of whoever asked for the migration, or {@code null} for the console.
	 */
	private final @Nullable UUID initiatorUniqueId;
}
