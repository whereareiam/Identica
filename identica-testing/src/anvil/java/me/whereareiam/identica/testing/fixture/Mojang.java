package me.whereareiam.identica.testing.fixture;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.environment.yggdrasil.YggdrasilMock;

/**
 * The local stand-in for Mojang that every network uses: Premium asks it whether a username belongs to a premium
 * account, and the proxy asks it to verify a premium login. A test registers the premium accounts that exist, so
 * its outcome depends neither on Mojang's services nor on a real account.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Mojang {
	private static final YggdrasilMock SERVICE = YggdrasilMock.start();

	/**
	 * Returns the service shared by the networks of this test run; each new network resets it.
	 */
	public static YggdrasilMock service() {
		return SERVICE;
	}
}
