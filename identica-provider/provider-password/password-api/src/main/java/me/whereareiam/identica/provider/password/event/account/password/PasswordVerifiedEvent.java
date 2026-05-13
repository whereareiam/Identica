package me.whereareiam.identica.provider.password.event.account.password;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.whereareiam.identica.event.base.Event;
import me.whereareiam.identica.event.base.SynchronousEvent;
import me.whereareiam.identica.provider.password.model.PasswordAccount;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a password account password was verified successfully.
 */
@Getter
@AllArgsConstructor
public class PasswordVerifiedEvent implements Event, SynchronousEvent {
	private final @NotNull PasswordAccount account;
	private final @NotNull String password;
}
