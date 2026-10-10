package me.whereareiam.identica.engine.pipeline.scenario;

import com.google.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Runs the pipeline for one connection one run at a time, in the order the requests arrived. A run loads the
 * player's state, changes it and saves it; two runs of one connection at once would each save over the other.
 * Runs of different connections do not wait for each other.
 */
@Singleton
public final class ConnectionRuns {
	private final Map<UUID, CompletableFuture<?>> last = new ConcurrentHashMap<>();

	/**
	 * Starts a run after every earlier run of the same connection has finished, however it finished.
	 *
	 * @param connectionUniqueId connection the run belongs to; a run without one starts immediately
	 * @param run starts the run
	 * @param <T> result type
	 * @return result of the run
	 */
	public <T> @NotNull CompletionStage<T> submit(@Nullable UUID connectionUniqueId, @NotNull Supplier<CompletionStage<T>> run) {
		if (connectionUniqueId == null) return start(run);

		CompletableFuture<T> next = new CompletableFuture<>();
		CompletableFuture<?> previous = last.put(connectionUniqueId, next);
		CompletableFuture<?> ready = previous == null ? CompletableFuture.completedFuture(null) : previous;
		ready.whenComplete((ignored, earlierFailure) -> start(run).whenComplete((result, failure) -> {
			last.remove(connectionUniqueId, next);
			if (failure != null) next.completeExceptionally(failure);
			else next.complete(result);
		}));

		return next;
	}

	private static <T> CompletionStage<T> start(Supplier<CompletionStage<T>> run) {
		try {
			return run.get();
		} catch (RuntimeException | Error failure) {
			return CompletableFuture.failedFuture(failure);
		}
	}
}
