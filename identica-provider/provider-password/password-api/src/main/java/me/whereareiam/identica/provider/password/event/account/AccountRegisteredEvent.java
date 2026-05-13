package me.whereareiam.identica.provider.password.event.account;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.provider.password.model.PasswordAccount;
import me.whereareiam.identica.provider.password.type.PasswordChangeReason;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a password account is registered.
 */
@Getter
@AllArgsConstructor
public class AccountRegisteredEvent implements Event {
	private final @NotNull PasswordAccount account;
	private final @NotNull PasswordChangeReason reason;
}
