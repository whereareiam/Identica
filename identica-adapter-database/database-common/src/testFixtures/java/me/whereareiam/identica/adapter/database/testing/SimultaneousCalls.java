package me.whereareiam.identica.adapter.database.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntFunction;

/**
 * Runs calls on separate threads that all start at the same moment, as proxies sharing a database
 * write the same row at once.
 */
public final class SimultaneousCalls {
	private static final long TIMEOUT_SECONDS = 60;

	/**
	 * Runs a call on each of a number of threads, released together, and waits for all of them.
	 *
	 * @param parties number of threads
	 * @param call    the call, given the index of its thread
	 * @param <T>     result of a call
	 * @return the results, in thread order
	 * @throws AssertionError when a call throws or does not finish in time
	 */
	public static <T> List<T> run(int parties, IntFunction<T> call) {
		CyclicBarrier start = new CyclicBarrier(parties);
		ExecutorService executor = Executors.newFixedThreadPool(parties);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (int index = 0; index < parties; index++) {
				int party = index;
				futures.add(executor.submit(() -> {
					start.await();
					return call.apply(party);
				}));
			}

			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));

			return results;
		} catch (ExecutionException e) {
			throw new AssertionError("A simultaneous call failed", e.getCause());
		} catch (InterruptedException | TimeoutException e) {
			throw new AssertionError("Simultaneous calls did not finish", e);
		} finally {
			executor.shutdownNow();
		}
	}
}
