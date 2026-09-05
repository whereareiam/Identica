package me.whereareiam.identica.trait.authoritative.username.model.account;

import lombok.*;
import me.whereareiam.identica.trait.authoritative.username.type.AccountUsernameSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Append-only history entry describing a persisted username change for an account.
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class AccountUsernameHistoryEntry {
	private @NotNull UUID uniqueId;
	private @Nullable String providerId;
	private @NotNull String oldUsername;
	private @NotNull String newUsername;
	private @NotNull AccountUsernameSource source;
	private long changedAt;
}
