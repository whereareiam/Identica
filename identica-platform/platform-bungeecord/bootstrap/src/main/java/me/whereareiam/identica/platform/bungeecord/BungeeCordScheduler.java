package me.whereareiam.identica.platform.bungeecord;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import me.whereareiam.identica.model.scheduler.*;
import me.whereareiam.identica.service.Scheduler;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.api.scheduler.TaskScheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Singleton
public class BungeeCordScheduler implements Scheduler {
	private final BungeeCordIdentica plugin;
	private final TaskScheduler scheduler;
	private final Map<JobKey, ScheduledTask> tasks = new ConcurrentHashMap<>();

	@Inject
	public BungeeCordScheduler(
			BungeeCordIdentica plugin,
			ProxyServer proxyServer
	) {
		this.plugin = plugin;
		this.scheduler = proxyServer.getScheduler();
	}

	@Override
	public void schedule(RunnableTask runnableTask) {
		ScheduledTask task = scheduler.runAsync(plugin, runnableTask.getRunnable());
		put(runnableTask.getKey(), task);
	}

	@Override
	public void schedule(DelayedRunnableTask runnableTask) {
		ScheduledTask task = scheduler.schedule(
				plugin,
				runnableTask.getRunnable(),
				runnableTask.getDelay(),
				TimeUnit.MILLISECONDS
		);
		put(runnableTask.getKey(), task);
	}

	@Override
	public void schedule(PeriodicalRunnableTask runnableTask) {
		ScheduledTask task = scheduler.schedule(
				plugin,
				runnableTask.getRunnable(),
				runnableTask.getDelay(),
				runnableTask.getPeriod(),
				TimeUnit.MILLISECONDS
		);
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
