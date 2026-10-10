package me.whereareiam.identica.platform.velocity;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import me.whereareiam.identica.model.scheduler.DelayedRunnableTask;
import me.whereareiam.identica.model.scheduler.JobKey;
import me.whereareiam.identica.model.scheduler.Origin;
import me.whereareiam.identica.model.scheduler.PeriodicalRunnableTask;
import me.whereareiam.identica.model.scheduler.RunnableTask;
import me.whereareiam.identica.service.Scheduler;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class VelocityScheduler implements Scheduler {
	private final VelocityIdentica plugin;
	private final com.velocitypowered.api.scheduler.Scheduler velocityScheduler;
	private final Map<JobKey, ScheduledTask> tasks = new ConcurrentHashMap<>();

	@Inject
	public VelocityScheduler(
			VelocityIdentica plugin,
			ProxyServer proxyServer
	) {
		this.plugin = plugin;
		this.velocityScheduler = proxyServer.getScheduler();
	}

	@Override
	public void schedule(RunnableTask runnableTask) {
		ScheduledTask task = velocityScheduler
				.buildTask(plugin, runnableTask.getRunnable())
				.schedule();

		put(runnableTask.getKey(), task);
	}

	@Override
	public void schedule(DelayedRunnableTask runnableTask) {
		ScheduledTask task = velocityScheduler
				.buildTask(plugin, runnableTask.getRunnable())
				.delay(Duration.ofMillis(runnableTask.getDelay()))
				.schedule();

		put(runnableTask.getKey(), task);
	}

	@Override
	public void schedule(PeriodicalRunnableTask runnableTask) {
		ScheduledTask task = velocityScheduler
				.buildTask(plugin, runnableTask.getRunnable())
				.delay(Duration.ofMillis(runnableTask.getDelay()))
				.repeat(Duration.ofMillis(runnableTask.getPeriod()))
				.schedule();

		put(runnableTask.getKey(), task);
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
		ScheduledTask task = tasks.remove(key);
		if (task != null)
			task.cancel();
	}

	@Override
	public void cancelByOrigin(Origin origin) {
		for (var iterator = tasks.entrySet().iterator(); iterator.hasNext(); ) {
			var entry = iterator.next();
			if (!entry.getKey().getOrigin().equals(origin))
				continue;
			entry.getValue().cancel();
			iterator.remove();
		}
	}

	private void put(JobKey key, ScheduledTask task) {
		cancel(key);
		tasks.put(key, task);
	}
}
