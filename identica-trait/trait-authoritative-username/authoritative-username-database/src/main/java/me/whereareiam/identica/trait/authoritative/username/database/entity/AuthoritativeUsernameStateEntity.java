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
@Entity(tableName = "identica_account_username_state")
public class AuthoritativeUsernameStateEntity implements EntitySchemaProvider {
	private UUID uniqueId;
	private String source;
	private long updatedAt;

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
				CREATE TABLE IF NOT EXISTS identica_account_username_state (
					unique_id %s NOT NULL,
					source VARCHAR(32) NOT NULL,
					updated_at %s NOT NULL,
					PRIMARY KEY (unique_id),
					FOREIGN KEY (unique_id)
						REFERENCES identica_accounts(unique_id)
						ON DELETE CASCADE
				)
				""".formatted(uuidType, timeType);
	}
}
