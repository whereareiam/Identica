package me.whereareiam.identica.provider.migration;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.model.message.PlayerMessage;
import org.jetbrains.annotations.Nullable;

@Getter
@Builder
@ToString
public class MigrationPrecheckResult {
	private boolean allowed;
	private @Nullable PlayerMessage message;
	private @Nullable PlayerMessage kickMessage;

	public static MigrationPrecheckResult allow() {
		return MigrationPrecheckResult.builder()
				.allowed(true)
				.build();
	}

	public static MigrationPrecheckResult allow(@Nullable PlayerMessage kickMessage) {
		return MigrationPrecheckResult.builder()
				.allowed(true)
				.kickMessage(kickMessage)
				.build();
	}

	public static MigrationPrecheckResult deny(@Nullable PlayerMessage message) {
		return MigrationPrecheckResult.builder()
				.allowed(false)
				.message(message)
				.build();
	}
}
