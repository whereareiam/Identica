package me.whereareiam.identica.feature.verification.database.entity;

import lombok.*;
import me.whereareiam.dialectica.EntitySchemaProvider;
import me.whereareiam.dialectica.annotation.Entity;

import java.util.UUID;

/**
 * The last time step whose one-time code an account's method accepted. A code of this step or an older one is
 * refused, so each code is used at most once.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Entity(tableName = "identica_verification_time_steps")
public class VerificationTimeStepEntity implements EntitySchemaProvider {
	private UUID uniqueId;
	private String methodId;
	private long lastStep;

	@Override
	public String statement(String databaseType) {
		String type = databaseType == null ? "" : databaseType.toUpperCase();
		String uuidType = switch (type) {
			case "POSTGRES", "H2" -> "UUID";
			case "SQLITE" -> "TEXT";
			default -> "CHAR(36)";
		};
		String stepType = "SQLITE".equals(type) ? "INTEGER" : "BIGINT";

		return """
				CREATE TABLE IF NOT EXISTS identica_verification_time_steps (
					unique_id %s NOT NULL,
					method_id VARCHAR(64) NOT NULL,
					last_step %s NOT NULL,
					PRIMARY KEY (unique_id, method_id),
					FOREIGN KEY (unique_id) REFERENCES identica_accounts(unique_id) ON DELETE CASCADE
				)
				""".formatted(uuidType, stepType);
	}
}
