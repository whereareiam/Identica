package me.whereareiam.identica.adapter.replication;

import me.whereareiam.identica.Reloadable;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.Registry;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public final class RedisTestFixtures {
	private RedisTestFixtures() {
	}

	public static Replication enabledReplication(String host, int port) {
		Replication replication = new Replication();
		replication.setEnabled(true);
		replication.setServerId("test-server");

		Replication.Redis redis = new Replication.Redis();
		redis.setHost(host);
		redis.setPort(port);
		redis.setPassword("");
		redis.setSsl(false);
		redis.setTimeout(200);

		Replication.Channels channels = new Replication.Channels();
		channels.setAccountUpdates("account-updates");
		channels.setSessions("sessions");
		channels.setConflicts("conflicts");
		channels.setEvents("events");
		redis.setChannels(channels);
		replication.setRedis(redis);

		Replication.Cache cache = new Replication.Cache();
		cache.setReservations("reservations");
		cache.setInstructions("instructions");
		cache.setPipelineState("pipeline-state");

		Replication.Sessions sessions = new Replication.Sessions();
		sessions.setRecords("sessions-records");
		sessions.setAccounts("sessions-accounts");
		sessions.setSubjects("sessions-subjects");
		cache.setSessions(sessions);
		replication.setCache(cache);

		return replication;
	}

	public static Registry<Reloadable> reloadableRegistry() {
		return new TestRegistry<>();
	}

	private static final class TestRegistry<T> implements Registry<T> {
		private final Set<T> values = new HashSet<>();

		@Override
		public void register(T value) {
			values.add(value);
		}

		@Override
		public void unregister(T value) {
			values.remove(value);
		}

		@Override
		public Set<T> values() {
			return Collections.unmodifiableSet(values);
		}
	}
}
