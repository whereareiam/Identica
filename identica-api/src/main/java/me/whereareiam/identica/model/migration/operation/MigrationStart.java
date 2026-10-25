package me.whereareiam.identica.model.migration.operation;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.type.migration.MigrationInitiator;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@Builder
@ToString
public class MigrationStart {
	private @Nullable UUID uniqueId;
	private @Nullable UUID connectionUniqueId;
	private @Nullable String targetProviderId;
	private @Nullable String username;
	private @Nullable String ip;
	private @Nullable PlayerMessage kickMessage;
	private @Nullable MigrationInitiator initiator;
	private @Nullable UUID initiatorUniqueId;
}
