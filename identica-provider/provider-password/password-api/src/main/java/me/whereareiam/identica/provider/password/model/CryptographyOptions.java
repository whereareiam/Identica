package me.whereareiam.identica.provider.password.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class CryptographyOptions {
	private int bcryptCost;
	private int argon2Iterations;
	private int argon2MemoryKb;
	private int argon2Parallelism;
}
