package me.whereareiam.identica.feature.sentinel.type;

import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelQuotaPolicy;
import me.whereareiam.identica.feature.sentinel.model.SentinelQuotaUsage;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelMessages;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelSettings;
import me.whereareiam.identica.identity.address.AccountAddressService;
import me.whereareiam.identica.model.message.PlayerMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Accounts Per Address Sentinel Quota")
class AccountsPerAddressSentinelQuotaTest {
	private static final SentinelContext CONTEXT = SentinelContext.builder().ip("203.0.113.10").build();

	private final SentinelSettings settings = settings();
	private final AccountAddressService addresses = mock(AccountAddressService.class);
	private final AccountsPerAddressSentinelQuota quota = new AccountsPerAddressSentinelQuota(
			() -> settings,
			AccountsPerAddressSentinelQuotaTest::messages,
			addresses
	);

	@DisplayName("Limits registrations to maxAccounts once turned on, and only at registration")
	@Test
	void limitsToMaxAccounts() {
		SentinelQuotaPolicy policy = quota.policy(CONTEXT);

		assertTrue(policy.isActive());
		assertEquals(3, policy.getLimit());
		assertEquals(List.of(SentinelScope.REGISTRATION), List.of(quota.scopes()));
	}

	@DisplayName("Limits nothing while it or the sentinel is off, or for an allowed address")
	@Test
	void limitsNothingWhenOffOrAllowed() {
		settings.getSentinels().getAccountsPerAddress().setAllowlist(List.of("not a range", "203.0.113.0/24"));
		assertFalse(quota.policy(CONTEXT).isActive());
		assertTrue(quota.policy(SentinelContext.builder().ip("198.51.100.1").build()).isActive());

		settings.getSentinels().getAccountsPerAddress().setEnabled(false);
		assertFalse(quota.policy(SentinelContext.builder().ip("198.51.100.1").build()).isActive());

		settings.getSentinels().getAccountsPerAddress().setEnabled(true);
		settings.setEnabled(false);
		assertFalse(quota.policy(SentinelContext.builder().ip("198.51.100.1").build()).isActive());
	}

	@DisplayName("Counts every account registered from the address, which never frees a slot without a window")
	@Test
	void countsEveryRegistrationWithoutAWindow() {
		when(addresses.findRegistrationTimes("203.0.113.10", Duration.ZERO)).thenReturn(List.of(1L, 2L, 3L));

		SentinelQuotaUsage usage = quota.usage(CONTEXT, quota.policy(CONTEXT));

		assertEquals(3, usage.getUsed());
		assertEquals(0L, usage.getFreesAt());
	}

	@DisplayName("Frees a slot once enough of the oldest registrations left the window")
	@Test
	void freesASlotWithinTheWindow() {
		settings.getSentinels().getAccountsPerAddress().setWindow(Duration.ofMillis(1000));
		when(addresses.findRegistrationTimes("203.0.113.10", Duration.ofMillis(1000))).thenReturn(List.of(100L, 200L, 300L, 400L));

		SentinelQuotaUsage usage = quota.usage(CONTEXT, quota.policy(CONTEXT));

		assertEquals(4, usage.getUsed());
		assertEquals(1200L, usage.getFreesAt());
	}

	@DisplayName("Words the refusal with the limit and the seconds left")
	@Test
	void wordsTheRefusal() {
		SentinelQuotaPolicy policy = quota.policy(CONTEXT);

		PlayerMessage now = policy.getMessageSupplier().apply(CONTEXT, SentinelQuotaUsage.of(3));
		assertEquals("sentinel:accounts-per-address", now.getId());
		assertEquals(Map.of("maxAccounts", "3", "seconds", "0"), now.getPlaceholders());
		String later = policy.getMessageSupplier().apply(CONTEXT, new SentinelQuotaUsage(3, System.currentTimeMillis() + 60_000L))
				.getPlaceholders()
				.get("seconds");
		assertTrue(later.equals("60") || later.equals("59"), later);
	}

	private static SentinelSettings settings() {
		SentinelSettings settings = new SentinelSettings();
		settings.setEnabled(true);
		settings.getSentinels().getAccountsPerAddress().setEnabled(true);
		settings.getSentinels().getAccountsPerAddress().setMaxAccounts(3);
		return settings;
	}

	private static SentinelMessages messages() {
		SentinelMessages messages = new SentinelMessages();
		SentinelMessages.AccountsPerAddress accountsPerAddress = new SentinelMessages.AccountsPerAddress();
		accountsPerAddress.setDenied(List.of("At most {maxAccounts}, wait {seconds}s."));
		messages.setAccountsPerAddress(accountsPerAddress);
		return messages;
	}
}
