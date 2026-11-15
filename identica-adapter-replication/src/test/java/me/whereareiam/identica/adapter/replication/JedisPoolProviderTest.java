package me.whereareiam.identica.adapter.replication;

import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.adapter.replication.provider.JedisPoolProvider;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.Registry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("Jedis Pool Provider")
class JedisPoolProviderTest {
	@DisplayName("Disabled replication never exposes a Redis pool")
	@Test
	void disabledReplicationThrows() {
		Replication replication = new Replication();
		replication.setEnabled(false);

		Registry<Reloadable> registry = RedisTestFixtures.reloadableRegistry();
		JedisPoolProvider provider = new JedisPoolProvider(() -> replication, registry);

		assertThrows(IllegalStateException.class, provider::get);
		provider.reload();
		assertThrows(IllegalStateException.class, provider::get);
	}

	@DisplayName("Fails to hand out a pool while Redis is unreachable")
	@Test
	void unreachableRedisThrows() {
		Replication replication = RedisTestFixtures.enabledReplication("127.0.0.1", 6399);
		replication.setEnabled(false);
		replication.getRedis().setTimeout(100);

		Registry<Reloadable> registry = RedisTestFixtures.reloadableRegistry();
		JedisPoolProvider provider = new JedisPoolProvider(() -> replication, registry);

		replication.setEnabled(true);
		assertThrows(IllegalStateException.class, provider::get);
	}
}
