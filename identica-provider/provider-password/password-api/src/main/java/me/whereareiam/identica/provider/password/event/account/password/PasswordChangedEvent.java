package me.whereareiam.identica.provider.password.event.account.password;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.provider.password.model.PasswordAccount;
import me.whereareiam.identica.provider.password.type.PasswordChangeReason;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a password account password has changed.
 */
@Getter
@AllArgsConstructor
public class PasswordChangedEvent implements Event {
	private final @NotNull PasswordAccount account;
	private final @NotNull PasswordChangeReason reason;
	private final long changedAt;
}
