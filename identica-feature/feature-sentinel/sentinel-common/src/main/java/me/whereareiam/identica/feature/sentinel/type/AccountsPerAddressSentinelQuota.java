package me.whereareiam.identica.feature.sentinel.type;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import lombok.RequiredArgsConstructor;
import me.whereareiam.identica.feature.sentinel.SentinelMessageDefinitions;
import me.whereareiam.identica.feature.sentinel.SentinelQuota;
import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelQuotaPolicy;
import me.whereareiam.identica.feature.sentinel.model.SentinelQuotaUsage;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelMessages;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelSettings;
import me.whereareiam.identica.identity.address.AccountAddressService;
import me.whereareiam.identica.logging.Logger;
import me.whereareiam.identica.model.identity.address.AddressRange;
import me.whereareiam.identica.model.message.PlayerMessage;
import me.whereareiam.identica.util.NetworkUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Refuses a registration once {@code maxAccounts} accounts were registered from the connection's address, within the
 * window when one is set. Identica keeps the addresses accounts register from; {@code identity/addresses.yml} decides
 * which addresses count as one.
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = @Inject)
public class AccountsPerAddressSentinelQuota implements SentinelQuota {
	/**
	 * Identifier of the quota.
	 */
	public static final String ID = "accounts-per-address";
	private static final SentinelScope[] SCOPES = new SentinelScope[]{SentinelScope.REGISTRATION};

	private final Provider<SentinelSettings> settingsProvider;
	private final Provider<SentinelMessages> messagesProvider;
	private final AccountAddressService addresses;
	private volatile @Nullable Allowlist allowlist;

	@Override
	public @NotNull String id() {
		return ID;
	}

	@Override
	public @NotNull SentinelScope[] scopes() {
		return SCOPES;
	}

	/**
	 * Limits nothing while the sentinel or this limit is off, or for an address on the allowlist.
	 */
	@Override
	public @NotNull SentinelQuotaPolicy policy(@NotNull SentinelContext context) {
		SentinelSettings settings = settingsProvider.get();
		SentinelSettings.AccountsPerAddress limit = settings.getSentinels().getAccountsPerAddress();
		if (!settings.isEnabled() || !limit.isEnabled() || isAllowed(limit.getAllowlist(), context.getIp()))
			return SentinelQuotaPolicy.disabled();

		SentinelQuotaPolicy policy = new SentinelQuotaPolicy();
		policy.setEnabled(true);
		policy.setLimit(limit.getMaxAccounts());
		policy.setMessageSupplier((ignored, usage) -> message(limit.getMaxAccounts(), usage));
		return policy;
	}

	/**
	 * Counts the accounts registered from the address's group. With a window, the usage drops below the limit once
	 * enough of the oldest registrations left it.
	 */
	@Override
	public @NotNull SentinelQuotaUsage usage(@NotNull SentinelContext context, @NotNull SentinelQuotaPolicy policy) {
		Duration window = settingsProvider.get().getSentinels().getAccountsPerAddress().getWindow();
		List<Long> registrations = addresses.findRegistrationTimes(context.getIp(), window);
		int used = registrations.size();
		if (!isPositive(window) || used < policy.getLimit()) return SentinelQuotaUsage.of(used);

		long leavingLast = registrations.get(used - policy.getLimit());
		return new SentinelQuotaUsage(used, leavingLast + window.toMillis());
	}

	/**
	 * Warns when the limit is on while {@code identity/addresses.yml} records no registration, which leaves it
	 * nothing to count.
	 */
	public void warnIfUncounted() {
		SentinelSettings settings = settingsProvider.get();
		if (!settings.isEnabled() || !settings.getSentinels().getAccountsPerAddress().isEnabled()) return;
		if (addresses.recording().recordsRegistrations()) return;

		Logger.warn("The sentinel's accountsPerAddress limit is on, but identity/addresses.yml records no addresses, so it limits nothing");
	}

	private @NotNull PlayerMessage message(int maxAccounts, @NotNull SentinelQuotaUsage usage) {
		long seconds = usage.getFreesAt() > 0 ? Math.max(0L, (usage.getFreesAt() - System.currentTimeMillis() + 999) / 1000) : 0L;

		return SentinelMessageDefinitions.ACCOUNTS_PER_ADDRESS.create(messagesProvider.get(), Map.of(
				"maxAccounts", String.valueOf(maxAccounts),
				"seconds", String.valueOf(seconds)
		));
	}

	private boolean isAllowed(@NotNull List<String> entries, @Nullable String ip) {
		InetAddress address = NetworkUtil.parseAddress(ip);
		if (address == null) return false;

		for (AddressRange range : allowlist(entries).ranges())
			if (range.contains(address)) return true;

		return false;
	}

	/**
	 * Returns the ranges of the allowlist, parsed again only after the settings were reloaded.
	 */
	private @NotNull Allowlist allowlist(@NotNull List<String> entries) {
		Allowlist current = allowlist;
		if (current != null && current.entries() == entries) return current;

		List<AddressRange> ranges = new ArrayList<>();
		for (String entry : entries) {
			AddressRange range = AddressRange.parse(entry).orElse(null);
			if (range == null) {
				Logger.warn("The sentinel's accountsPerAddress allowlist holds \"%s\", which is not an address or range; it is skipped", entry);
				continue;
			}
			ranges.add(range);
		}

		Allowlist parsed = new Allowlist(entries, List.copyOf(ranges));
		allowlist = parsed;
		return parsed;
	}

	private static boolean isPositive(@NotNull Duration duration) {
		return !duration.isZero() && !duration.isNegative();
	}

	private record Allowlist(@NotNull List<String> entries, @NotNull List<AddressRange> ranges) {
	}
}
