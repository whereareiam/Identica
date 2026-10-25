package me.whereareiam.identica.model.migration.operation;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.model.message.PlayerMessage;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@Builder
@ToString
public class MigrationConfirm {
	private @Nullable UUID connectionUniqueId;
	private @Nullable PlayerMessage kickMessage;
}
