package me.whereareiam.identica.feature.sentinel;

import me.whereareiam.identica.feature.sentinel.model.SentinelContext;
import me.whereareiam.identica.feature.sentinel.model.SentinelDecision;
import me.whereareiam.identica.feature.sentinel.model.SentinelPolicy;
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
		var registry = new SentinelRegistry();
		var definition = mock(SentinelDefinition.class);
		when(definition.id()).thenReturn("credential-rule");
		when(definition.providerId()).thenReturn("credential");
		when(definition.scopes()).thenReturn(new me.whereareiam.identica.feature.sentinel.type.SentinelScope[]{me.whereareiam.identica.feature.sentinel.type.SentinelScope.PROCESS});
		when(definition.policy(any())).thenReturn(new me.whereareiam.identica.feature.sentinel.model.SentinelPolicy());
		registry.register(definition);
		var service = new DefaultSentinelService(registry, features, replication, SentinelSettings::new);
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

	@DisplayName("Service reads the cache namespace from sentinel feature settings")
	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void serviceReadsCacheNamespaceFromFeatureSettings() {
		ReplicationSystem replicationSystem = mock(ReplicationSystem.class);
		ReplicationCacheBuilder cacheBuilder = mock(ReplicationCacheBuilder.class);
		ReplicatedCache cache = mock(ReplicatedCache.class);
		when(replicationSystem.cache("identica:test:sentinels")).thenReturn(cacheBuilder);
		when(cacheBuilder.replicated(any())).thenReturn(cache);

		SentinelSettings settings = new SentinelSettings();
		SentinelSettings.Replication replicationSettings = new SentinelSettings.Replication();
		replicationSettings.setState("identica:test:sentinels");
		settings.setReplication(replicationSettings);

		new DefaultSentinelService(new SentinelRegistry(), mock(me.whereareiam.identica.feature.FeatureRegistry.class), replicationSystem, () -> settings);

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

		SentinelRegistry registry = new SentinelRegistry();
		registry.register(new SentinelDefinition() {
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
		});
		DefaultSentinelService service = new DefaultSentinelService(
				registry,
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
}
