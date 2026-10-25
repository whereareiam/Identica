package me.whereareiam.identica.model.migration.operation;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.type.migration.MigrationResultStatus;
import org.jetbrains.annotations.Nullable;

@Getter
@Builder
@ToString
public class MigrationResult {
	private MigrationResultStatus status;
	private @Nullable PlayerMessage message;
}
