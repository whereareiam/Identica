package me.whereareiam.identica.engine.pipeline.scenario;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("Connection Runs")
class ConnectionRunsTest {
	private final ConnectionRuns runs = new ConnectionRuns();
	private final List<String> started = new ArrayList<>();

	@DisplayName("A run starts only after the earlier run of the same connection has finished")
	@Test
	void runsOfOneConnectionDoNotOverlap() {
		UUID connection = UUID.randomUUID();
		CompletableFuture<String> first = new CompletableFuture<>();

		CompletableFuture<String> firstResult = runs.submit(connection, () -> start("first", first)).toCompletableFuture();
		CompletableFuture<String> secondResult = runs.submit(connection, () -> start("second", CompletableFuture.completedFuture("b")))
				.toCompletableFuture();

		assertEquals(List.of("first"), started);
		assertFalse(secondResult.isDone());

		first.complete("a");

		assertEquals(List.of("first", "second"), started);
		assertEquals("a", firstResult.join());
		assertEquals("b", secondResult.join());
	}

	@DisplayName("Runs of different connections do not wait for each other")
	@Test
	void otherConnectionsStartImmediately() {
		runs.submit(UUID.randomUUID(), () -> start("first", new CompletableFuture<>()));
		CompletableFuture<String> other = runs.submit(UUID.randomUUID(), () -> start("other", CompletableFuture.completedFuture("b")))
				.toCompletableFuture();

		assertEquals(List.of("first", "other"), started);
		assertEquals("b", other.join());
	}

	@DisplayName("A failed run does not block the next one")
	@Test
	void aFailedRunLetsTheNextOneStart() {
		UUID connection = UUID.randomUUID();

		CompletableFuture<String> failed = runs.<String>submit(connection, () -> {
			throw new IllegalStateException("broken");
		}).toCompletableFuture();
		CompletableFuture<String> next = runs.submit(connection, () -> start("next", CompletableFuture.completedFuture("b")))
				.toCompletableFuture();

		assertTrue(failed.isCompletedExceptionally());
		assertEquals("b", next.join());
	}

	private CompletableFuture<String> start(String name, CompletableFuture<String> result) {
		started.add(name);
		return result;
	}
}
