package me.whereareiam.identica.provider.password.config.defaults;

import com.google.inject.Singleton;
import me.whereareiam.configura.merge.defaults.MergeDefaultsProvider;
import me.whereareiam.identica.provider.password.config.PasswordSettings;

import java.time.Duration;

@Singleton
public class PasswordSettingsDefaults implements MergeDefaultsProvider<PasswordSettings> {
	@Override
	public PasswordSettings supply(PasswordSettings config) {
		PasswordSettings.Scenario.Registration registration = new PasswordSettings.Scenario.Registration();
		registration.setEnabled(true);
		registration.setRequireRepeat(true);

		PasswordSettings.Scenario.Authentication authentication = new PasswordSettings.Scenario.Authentication();
		PasswordSettings.Scenario.Authentication.Bruteforce bruteforce = new PasswordSettings.Scenario.Authentication.Bruteforce();
		bruteforce.setMaxAttempts(5);
		PasswordSettings.Scenario.Authentication.Bruteforce.Lockout lockout =
				new PasswordSettings.Scenario.Authentication.Bruteforce.Lockout();
		lockout.setEnabled(true);
		lockout.setDuration(Duration.ofSeconds(300));
		bruteforce.setLockout(lockout);
		PasswordSettings.Scenario.Authentication.Bruteforce.Warning warning =
				new PasswordSettings.Scenario.Authentication.Bruteforce.Warning();
		warning.setEnabled(true);
		warning.setThresholdPercentage(50);
		bruteforce.setWarning(warning);
		authentication.setBruteforce(bruteforce);

		PasswordSettings.Scenario scenario = new PasswordSettings.Scenario();
		scenario.setRegistration(registration);
		scenario.setAuthentication(authentication);
		PasswordSettings.Scenario.ChangePassword changePassword = new PasswordSettings.Scenario.ChangePassword();
		changePassword.setRequireRepeat(true);
		scenario.setChangePassword(changePassword);
		config.setScenario(scenario);

		PasswordSettings.Scenario.Registration.Username username = new PasswordSettings.Scenario.Registration.Username();
		username.setMinLength(3);
		username.setMaxLength(16);
		username.setPattern("^[a-zA-Z0-9_]+$");

		PasswordSettings.Scenario.Registration.Password password = new PasswordSettings.Scenario.Registration.Password();
		password.setMinLength(6);
		password.setMaxLength(32);
		password.setMinUpper(1);
		password.setMinLower(1);
		password.setMinNumber(1);
		password.setMinSpecial(1);
		registration.setUsername(username);
		registration.setPassword(password);

		PasswordSettings.Cryptography cryptography = new PasswordSettings.Cryptography();
		cryptography.setAlgorithm("bcrypt");
		cryptography.setAutoupgrade(true);

		PasswordSettings.Cryptography.Algorithms algorithms = new PasswordSettings.Cryptography.Algorithms();

		PasswordSettings.Cryptography.Algorithms.Bcrypt bcrypt = new PasswordSettings.Cryptography.Algorithms.Bcrypt();
		bcrypt.setCost(12);
		algorithms.setBcrypt(bcrypt);

		PasswordSettings.Cryptography.Algorithms.Argon2 argon2 = new PasswordSettings.Cryptography.Algorithms.Argon2();
		argon2.setIterations(3);
		argon2.setParallelism(1);
		argon2.setMemoryKb(65536);
		algorithms.setArgon2(argon2);

		cryptography.setAlgorithms(algorithms);

		config.setCryptography(cryptography);

		PasswordSettings.Replication.Cache cache = new PasswordSettings.Replication.Cache();
		cache.setLockout("password-lockout");
		PasswordSettings.Replication replication = new PasswordSettings.Replication();
		replication.setCache(cache);
		config.setReplication(replication);

		return config;
	}
}
