package me.whereareiam.identica.provider.password.model.authentication;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import me.whereareiam.identica.provider.password.model.PasswordAccount;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

@Getter
@ToString
@AllArgsConstructor
public class AuthenticationAttemptContext {
	private final @NotNull PasswordAccount account;
	private final @Nullable UUID connectionUniqueId;
	private final @Nullable UUID identityUniqueId;
	private final @Nullable String username;
	private final @Nullable String ip;
}
