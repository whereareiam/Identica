package me.whereareiam.identica.feature.sentinel;

import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelDecision;
import me.whereareiam.identica.feature.sentinel.model.SentinelPolicy;
import me.whereareiam.identica.feature.sentinel.model.SentinelQuotaPolicy;
import me.whereareiam.identica.feature.sentinel.model.SentinelQuotaUsage;
import me.whereareiam.identica.feature.sentinel.model.config.SentinelSettings;
import me.whereareiam.identica.feature.sentinel.type.SentinelMode;
import me.whereareiam.identica.feature.sentinel.type.SentinelScope;
import me.whereareiam.identica.replication.ReplicationSystem;
import me.whereareiam.identica.replication.cache.ReplicatedCache;
import me.whereareiam.identica.replication.cache.base.ReplicationCacheBuilder;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Default Sentinel Service")
class DefaultSentinelServiceTest {
	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void providerOwnedRulesNeverEvaluateAnotherProviderAndDisabledProvidersSkipChecks() {
		var features = mock(me.whereareiam.identica.feature.FeatureRegistry.class);
		when(features.isEnabled(anyString(), eq("sentinel"))).thenReturn(true);
		var replication = mock(ReplicationSystem.class);
		var builder = mock(ReplicationCacheBuilder.class);
		when(replication.cache(anyString())).thenReturn(builder);
		when(builder.replicated(any())).thenReturn(mock(ReplicatedCache.class));
		var registry = new SentinelRegistry<SentinelDefinition>();
		var definition = mock(SentinelDefinition.class);
		when(definition.id()).thenReturn("credential-rule");
		when(definition.providerId()).thenReturn("credential");
		when(definition.scopes()).thenReturn(new me.whereareiam.identica.feature.sentinel.type.SentinelScope[]{me.whereareiam.identica.feature.sentinel.type.SentinelScope.PROCESS});
		when(definition.policy(any())).thenReturn(new me.whereareiam.identica.feature.sentinel.model.SentinelPolicy());
		registry.register(definition);
		var service = new DefaultSentinelService(registry, new SentinelRegistry<>(), features, replication, SentinelSettings::new);
		service.evaluate(me.whereareiam.identica.feature.sentinel.type.SentinelScope.PROCESS,
				me.whereareiam.identica.feature.sentinel.model.SentinelContext.builder().providerId("premium").build());
		verify(definition, never()).policy(any());
		service.evaluate(me.whereareiam.identica.feature.sentinel.type.SentinelScope.PROCESS,
				me.whereareiam.identica.feature.sentinel.model.SentinelContext.builder().providerId("credential").build());
		verify(definition).policy(any());
		clearInvocations(definition);
		when(features.isEnabled("credential", "sentinel")).thenReturn(false);
		service.evaluate(me.whereareiam.identica.feature.sentinel.type.SentinelScope.PROCESS,
				me.whereareiam.identica.feature.sentinel.model.SentinelContext.builder().providerId("credential").build());
		verifyNoInteractions(definition);
	}

	@DisplayName("Service reads the cache namespace from sentinel feature settings when it first uses the cache")
	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void serviceReadsCacheNamespaceFromFeatureSettings() {
		ReplicationSystem replicationSystem = mock(ReplicationSystem.class);
		ReplicationCacheBuilder cacheBuilder = mock(ReplicationCacheBuilder.class);
		ReplicatedCache cache = mock(ReplicatedCache.class);
		when(replicationSystem.cache("identica:test:sentinels")).thenReturn(cacheBuilder);
		when(cacheBuilder.replicated(any())).thenReturn(cache);
		when(cache.invalidate(anyString())).thenReturn(CompletableFuture.completedFuture(null));

		SentinelSettings settings = new SentinelSettings();
		SentinelSettings.Replication replicationSettings = new SentinelSettings.Replication();
		replicationSettings.setState("identica:test:sentinels");
		settings.setReplication(replicationSettings);
		SentinelPolicy policy = new SentinelPolicy();
		policy.setEnabled(true);
		SentinelRegistry<SentinelDefinition> registry = new SentinelRegistry<>();
		registry.register(definition(policy));

		DefaultSentinelService service = new DefaultSentinelService(registry, new SentinelRegistry<>(), mock(me.whereareiam.identica.feature.FeatureRegistry.class), replicationSystem, () -> settings);
		verifyNoInteractions(replicationSystem);

		service.clear("test-rule", SentinelContext.builder().username("player").ip("203.0.113.10").build());

		verify(replicationSystem).cache("identica:test:sentinels");
	}

	@DisplayName("A lockout denies whether or not its message has text")
	@ParameterizedTest(name = "message \"{0}\"")
	@ValueSource(strings = {"", "Locked for {seconds}s"})
	@SuppressWarnings({"unchecked", "rawtypes"})
	void lockoutDeniesWithOrWithoutText(String message) {
		ReplicationSystem replicationSystem = mock(ReplicationSystem.class);
		ReplicationCacheBuilder cacheBuilder = mock(ReplicationCacheBuilder.class);
		ReplicatedCache cache = mock(ReplicatedCache.class);
		when(replicationSystem.cache(anyString())).thenReturn(cacheBuilder);
		when(cacheBuilder.replicated(any())).thenReturn(cache);
		when(cache.get(anyString())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
		when(cache.put(anyString(), any(), anyLong())).thenReturn(CompletableFuture.completedFuture(null));

		SentinelPolicy policy = new SentinelPolicy();
		policy.setEnabled(true);
		policy.setMaxAttempts(1);
		SentinelPolicy.Lockout lockout = new SentinelPolicy.Lockout();
		lockout.setDuration(Duration.ofSeconds(30));
		lockout.setMessageSupplier((context, seconds) -> message);
		policy.setLockout(lockout);

		SentinelRegistry<SentinelDefinition> registry = new SentinelRegistry<>();
		registry.register(definition(policy));
		DefaultSentinelService service = new DefaultSentinelService(
				registry,
				new SentinelRegistry<>(),
				mock(me.whereareiam.identica.feature.FeatureRegistry.class),
				replicationSystem,
				SentinelSettings::new
		);

		SentinelDecision decision = service.record(
				"test-rule",
				SentinelContext.builder().username("player").ip("203.0.113.10").build()
		);

		assertTrue(decision.isLimited());
		assertTrue(decision.isDeny());
		assertEquals(message, decision.getMessage());
	}

	@DisplayName("Refuses a connection whose usage reached a quota's limit, with the quota's message and the time left")
	@Test
	void refusesAReachedQuota() {
		long freesAt = System.currentTimeMillis() + 30_000L;
		DefaultSentinelService service = quotaService(quota(3, new SentinelQuotaUsage(3, freesAt)));

		SentinelDecision decision = service.evaluate(SentinelScope.REGISTRATION, SentinelContext.builder().ip("203.0.113.10").build()).orElseThrow();

		assertTrue(decision.isLimited());
		assertTrue(decision.isDeny());
		assertEquals("limit 3 used 3", decision.getMessage());
		assertTrue(decision.getRemainingSeconds() > 25 && decision.getRemainingSeconds() <= 30);
	}

	@DisplayName("Lets a connection through below the limit, at other scopes and while a provider has the sentinel off")
	@Test
	void letsAConnectionThroughBelowTheQuota() {
		DefaultSentinelService below = quotaService(quota(3, SentinelQuotaUsage.of(2)));
		assertTrue(below.evaluate(SentinelScope.REGISTRATION, SentinelContext.builder().build()).isEmpty());

		DefaultSentinelService reached = quotaService(quota(3, SentinelQuotaUsage.of(3)));
		assertTrue(reached.evaluate(SentinelScope.PROCESS, SentinelContext.builder().build()).isEmpty());
		assertTrue(reached.evaluate(SentinelScope.REGISTRATION, SentinelContext.builder().providerId("premium").build()).isEmpty());
		assertEquals(0L, reached.evaluate(SentinelScope.REGISTRATION, SentinelContext.builder().build()).orElseThrow().getRemainingSeconds());
	}

	@DisplayName("Counts nothing for an inactive quota")
	@Test
	void skipsAnInactiveQuota() {
		SentinelQuota quota = mock(SentinelQuota.class);
		when(quota.scopes()).thenReturn(new SentinelScope[]{SentinelScope.REGISTRATION});
		when(quota.policy(any())).thenReturn(SentinelQuotaPolicy.disabled());

		assertTrue(quotaService(quota).evaluate(SentinelScope.REGISTRATION, SentinelContext.builder().build()).isEmpty());
		verify(quota, never()).usage(any(), any());
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static DefaultSentinelService quotaService(SentinelQuota quota) {
		var features = mock(me.whereareiam.identica.feature.FeatureRegistry.class);
		when(features.isEnabled(anyString(), eq("sentinel"))).thenAnswer(call -> !"premium".equals(call.getArgument(0)));
		var replication = mock(ReplicationSystem.class);
		var builder = mock(ReplicationCacheBuilder.class);
		when(replication.cache(anyString())).thenReturn(builder);
		when(builder.replicated(any())).thenReturn(mock(ReplicatedCache.class));
		SentinelRegistry<SentinelQuota> quotas = new SentinelRegistry<>();
		quotas.register(quota);

		return new DefaultSentinelService(new SentinelRegistry<>(), quotas, features, replication, SentinelSettings::new);
	}

	private static SentinelQuota quota(int limit, SentinelQuotaUsage usage) {
		return new SentinelQuota() {
			@Override
			public @NotNull String id() {
				return "test-quota";
			}

			@Override
			public @NotNull SentinelScope[] scopes() {
				return new SentinelScope[]{SentinelScope.REGISTRATION};
			}

			@Override
			public @NotNull SentinelQuotaPolicy policy(@NotNull SentinelContext context) {
				SentinelQuotaPolicy policy = new SentinelQuotaPolicy();
				policy.setEnabled(true);
				policy.setLimit(limit);
				policy.setMessageSupplier((ignored, current) -> "limit " + limit + " used " + current.getUsed());
				return policy;
			}

			@Override
			public @NotNull SentinelQuotaUsage usage(@NotNull SentinelContext context, @NotNull SentinelQuotaPolicy policy) {
				return usage;
			}
		};
	}

	private static SentinelDefinition definition(SentinelPolicy policy) {
		return new SentinelDefinition() {
			@Override
			public @NotNull String id() {
				return "test-rule";
			}

			@Override
			public @NotNull SentinelScope[] scopes() {
				return new SentinelScope[]{SentinelScope.PROCESS};
			}

			@Override
			public @NotNull SentinelMode modeFor(@NotNull SentinelScope scope) {
				return SentinelMode.RECORD;
			}

			@Override
			public @NotNull SentinelPolicy policy(@NotNull SentinelContext context) {
				return policy;
			}
		};
	}
}
