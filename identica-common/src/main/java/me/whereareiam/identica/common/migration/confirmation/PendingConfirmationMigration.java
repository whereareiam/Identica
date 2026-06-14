package me.whereareiam.identica.common.migration.confirmation;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.type.migration.MigrationInitiator;

import java.util.UUID;

@Getter
@ToString
@AllArgsConstructor
public final class PendingConfirmationMigration {
	private final UUID uniqueId;
	private final UUID connectionUniqueId;
	private final String targetProviderId;
	private final String username;
	private final String ip;
	private final MigrationInitiator initiator;
	private final UUID initiatorUniqueId;
	private final long requestedAt;
}
