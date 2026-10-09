package me.whereareiam.identica.trait.authoritative.username.database.entity;

import lombok.*;
import me.whereareiam.dialectica.EntitySchemaProvider;
import me.whereareiam.dialectica.annotation.Entity;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Entity(tableName = "identica_account_username_history")
public class AuthoritativeUsernameHistoryEntity implements EntitySchemaProvider {
	private UUID uniqueId;
	private String providerId;
	private String oldUsername;
	private String newUsername;
	private String source;
	private long changedAt;

	@Override
	public String statement(String databaseType) {
		String type = databaseType == null ? "" : databaseType.toUpperCase();
		String uuidType = switch (type) {
			case "POSTGRES", "H2" -> "UUID";
			case "SQLITE" -> "TEXT";
			default -> "CHAR(36)";
		};
		String timeType = "SQLITE".equals(type) ? "INTEGER" : "BIGINT";

		return """
				CREATE TABLE IF NOT EXISTS identica_account_username_history (
					unique_id %s NOT NULL,
					provider_id VARCHAR(64),
					old_username VARCHAR(64) NOT NULL,
					new_username VARCHAR(64) NOT NULL,
					source VARCHAR(32) NOT NULL,
					changed_at %s NOT NULL,
					FOREIGN KEY (unique_id)
						REFERENCES identica_accounts(unique_id)
						ON DELETE CASCADE
				)
				;
				CREATE INDEX IF NOT EXISTS identica_account_username_history_unique_id_changed_at_idx
					ON identica_account_username_history (unique_id, changed_at)
				""".formatted(uuidType, timeType);
	}
}
