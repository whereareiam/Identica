package me.whereareiam.identica.feature.verification.database.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import me.whereareiam.dialectica.entity.annotation.Default;
import me.whereareiam.dialectica.entity.annotation.Id;
import me.whereareiam.dialectica.entity.annotation.Length;
import me.whereareiam.dialectica.entity.annotation.Nullable;
import me.whereareiam.dialectica.entity.annotation.References;
import me.whereareiam.dialectica.entity.annotation.Table;
import me.whereareiam.dialectica.schema.type.ReferentialAction;
import me.whereareiam.identica.database.schema.AccountTable;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
@Table(VerificationEnrollmentEntity.TABLE)
public class VerificationEnrollmentEntity {
	public static final String TABLE = "identica_verification_enrollments";
	public static final String ACCEPTED_COUNTER = "accepted_counter";

	@Id
	@References(table = AccountTable.NAME, column = AccountTable.KEY, onDelete = ReferentialAction.CASCADE)
	private UUID uniqueId;
	@Id
	@Length(64)
	private String methodId;
	@Id
	@Length(64)
	private String enrollmentId;
	private String credential;
	@Nullable
	@Length(64)
	private String label;
	@Nullable
	private Long createdAt;
	@Nullable
	private Long enabledAt;
	/** Last TOTP time step or HOTP counter a code was accepted for; never decreases. */
	@Default("0")
	private long acceptedCounter;
}
