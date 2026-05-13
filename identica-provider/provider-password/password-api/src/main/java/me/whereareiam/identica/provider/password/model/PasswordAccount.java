package me.whereareiam.identica.provider.password.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PasswordAccount {
	private @NotNull String providerId;
	private @NotNull String providerSubject;
	private @NotNull String passwordHash;
	private @NotNull String hashingMethod;
	private long createdAt;
	private long updatedAt;
}
