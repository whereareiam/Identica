package me.whereareiam.identica.common.config.defaults;

import me.whereareiam.configura.Config;
import me.whereareiam.configura.Configura;
import me.whereareiam.configura.type.Format;
import me.whereareiam.identica.model.config.Replication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Replication Defaults")
class ReplicationDefaultsTest {
	@DisplayName("Generated replication file no longer includes sentinel-owned cache namespaces")
	@Test
	void generatedReplicationFileNoLongerIncludesSentinelOwnedCacheNamespace(@TempDir Path tempDir) throws Exception {
		Path replicationPath = tempDir.resolve("replication.yml");
		Configura yaml = Config.builder()
				.format(Format.YAML)
				.defaults(ReplicationDefaults.class)
				.build();

		yaml.update(replicationPath, Replication.class);

		String generated = Files.readString(replicationPath);
		assertTrue(generated.contains("attempts:"));
		assertTrue(generated.contains("identica:provider-attempts"));
		assertFalse(generated.contains("sentinels: identica:sentinels"));
	}

	@DisplayName("A heartbeat timeout below three seconds is refused, and three seconds is accepted")
	@Test
	void heartbeatTimeoutHasAMinimum() {
		Replication replication = new ReplicationDefaults().supply(new Replication());
		assertEquals(30_000L, replication.heartbeatTimeoutMillis());

		replication.setHeartbeatTimeout(Duration.ofSeconds(3));
		assertEquals(3_000L, replication.heartbeatTimeoutMillis());

		replication.setHeartbeatTimeout(Duration.ofMillis(2_999));
		assertThrows(IllegalStateException.class, replication::heartbeatTimeoutMillis);
	}
}
