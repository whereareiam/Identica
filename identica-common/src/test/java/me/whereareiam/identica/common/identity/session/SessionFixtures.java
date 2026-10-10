package me.whereareiam.identica.common.identity.session;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.identica.common.replication.ReplicationTestFixtures;
import me.whereareiam.identica.model.Session;
import me.whereareiam.identica.model.SessionConnection;
import me.whereareiam.identica.model.config.Replication;
import me.whereareiam.identica.model.scheduler.DelayedRunnableTask;
import me.whereareiam.identica.model.scheduler.JobKey;
import me.whereareiam.identica.model.scheduler.Origin;
import me.whereareiam.identica.model.scheduler.PeriodicalRunnableTask;
import me.whereareiam.identica.model.scheduler.RunnableTask;
import me.whereareiam.identica.service.Scheduler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What the session tests share: a store standing in for Redis, the session namespaces, sessions, and a
 * scheduler whose periodic tasks the test runs itself.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class SessionFixtures {
	static final long LIFETIME_MS = 30_000L;

	/** An adapter that keeps what is written and delivers what is published, like a shared Redis. */
	static ReplicationTestFixtures.TestReplicationAdapter sharedStore() {
		ReplicationTestFixtures.TestReplicationAdapter adapter = new ReplicationTestFixtures.TestReplicationAdapter();
		adapter.storing = true;
		adapter.delivering = true;
		return adapter;
	}

	static Replication replication(String serverId) {
		Replication replication = new Replication();
		replication.setEnabled(true);
		replication.setServerId(serverId);

		Replication.Cache cache = new Replication.Cache();
		Replication.Sessions sessions = new Replication.Sessions();
		sessions.setRecords("sessions:records");
		sessions.setAccounts("sessions:accounts");
		sessions.setSubjects("sessions:subjects");
		cache.setSessions(sessions);
		replication.setCache(cache);

		Replication.Redis redis = new Replication.Redis();
		Replication.Channels channels = new Replication.Channels();
		channels.setSessions("identica:sessions");
		channels.setEvents("identica:events");
		redis.setChannels(channels);
		replication.setRedis(redis);

		return replication;
	}

	/** A session of an account whose provider subject is the account id, without a connection. */
	static Session session(UUID uniqueId) {
		return Session.builder()
				.uniqueId(uniqueId)
				.providerId("provider")
				.providerSubject(uniqueId.toString())
				.originalUsername("Player")
				.build();
	}

	/** A stored session: one that already has its id, creation time and connection. */
	static Session stored(UUID uniqueId, String serverId, long createdAt) {
		Session session = session(uniqueId);
		session.setSessionId(UUID.randomUUID().toString());
		session.setCreatedAt(createdAt);
		session.setConnection(new SessionConnection(serverId, UUID.randomUUID()));
		return session;
	}

	static final class ManualScheduler implements Scheduler {
		private final Map<JobKey, PeriodicalRunnableTask> periodicalTasks = new HashMap<>();

		@Override
		public void schedule(RunnableTask runnableTask) {
		}

		@Override
		public void schedule(DelayedRunnableTask runnableTask) {
		}

		@Override
		public void schedule(PeriodicalRunnableTask runnableTask) {
			periodicalTasks.put(runnableTask.getKey(), runnableTask);
		}

		@Override
		public void schedule(RunnableTask runnableTask, boolean async) {
			schedule(runnableTask);
		}

		@Override
		public void schedule(DelayedRunnableTask runnableTask, boolean async) {
			schedule(runnableTask);
		}

		@Override
		public void schedule(PeriodicalRunnableTask runnableTask, boolean async) {
			schedule(runnableTask);
		}

		@Override
		public void cancel(JobKey key) {
			periodicalTasks.remove(key);
		}

		@Override
		public void cancelByOrigin(Origin origin) {
			periodicalTasks.keySet().removeIf(key -> key.getOrigin().equals(origin));
		}

		/** The period of the only periodic task, in milliseconds. */
		long period() {
			return periodicalTasks.values().iterator().next().getPeriod();
		}

		boolean scheduled() {
			return !periodicalTasks.isEmpty();
		}

		/** Runs every periodic task once, as the scheduler does when the period has passed. */
		void tick() {
			for (PeriodicalRunnableTask task : periodicalTasks.values().toArray(PeriodicalRunnableTask[]::new))
				task.getRunnable().run();
		}
	}
}
