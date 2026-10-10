package me.whereareiam.identica.testing.fixture;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.model.player.AuthenticationAccount;
import me.whereareiam.anvil.api.scenario.ScenarioContext;
import org.junit.jupiter.api.Assumptions;

/**
 * The developer's stored premium account. Tests that need it are tagged {@code premium} and skip themselves on
 * machines without it, such as CI.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PremiumAccount {
	public static final String TAG = "premium";

	private static final String ACCOUNT = "main";

	/**
	 * Returns the stored account, or skips the calling test when this machine has none.
	 */
	public static AuthenticationAccount require(ScenarioContext anvil) {
		AuthenticationAccount account = anvil.accounts().list().stream()
				.filter(stored -> stored.getAccountId().equals(ACCOUNT))
				.findFirst()
				.orElse(null);
		Assumptions.assumeTrue(account != null, "No premium account '" + ACCOUNT + "' is stored on this machine; "
				+ "sign in with ./gradlew :identica-testing:anvilAccount --login=" + ACCOUNT);

		return account;
	}
}
