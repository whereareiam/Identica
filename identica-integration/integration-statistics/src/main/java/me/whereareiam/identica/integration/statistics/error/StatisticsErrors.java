package me.whereareiam.identica.integration.statistics.error;

import me.whereareiam.identica.integration.statistics.OfficialProviders;
import me.whereareiam.statistica.error.ErrorPolicy;
import org.jetbrains.annotations.NotNull;

import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.sql.SQLInvalidAuthorizationSpecException;
import java.sql.SQLTransientConnectionException;
import java.util.regex.Pattern;

/**
 * Which of Identica's errors are reported.
 * <p>
 * Errors of Identica and of its own providers are reported; a third-party provider's are not.
 * A database or Redis that cannot be reached, or refuses the configured credentials, is a server's
 * setup, not a bug, and is left out.
 */
public final class StatisticsErrors {
	private static final Pattern REFUSED_CREDENTIALS = Pattern.compile(
			"Access denied for user|password authentication failed|Wrong user name or password"
	);

	/**
	 * Returns the policy of Identica's error reports.
	 *
	 * @param officialProviders tells Identica's providers apart, whose class loaders change on reload
	 * @return the policy
	 */
	public static @NotNull ErrorPolicy policy(@NotNull OfficialProviders officialProviders) {
		return ErrorPolicy.builder()
				.owners(officialProviders::classLoaders)
				.ignore(ConnectException.class)
				.ignore(NoRouteToHostException.class)
				.ignore(SocketTimeoutException.class)
				.ignore(UnknownHostException.class)
				.ignore(SQLInvalidAuthorizationSpecException.class)
				.ignore(SQLTransientConnectionException.class)
				.ignore(REFUSED_CREDENTIALS)
				.build();
	}
}
