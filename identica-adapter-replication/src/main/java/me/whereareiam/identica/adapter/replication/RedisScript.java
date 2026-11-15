package me.whereareiam.identica.adapter.replication;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisNoScriptException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * A Lua script Redis runs as one step, so no other client changes the keys it touches in between. It is called by
 * its SHA1 digest, so a call sends the digest instead of the script. Redis keeps a script it ran until it restarts
 * or its script cache is flushed; a call it answers with {@code NOSCRIPT} loads the script and runs it again.
 * <p>
 * Every script takes the entry as {@code KEYS[1]}, its namespace's index as {@code KEYS[2]} and the entry's index
 * member as {@code ARGV[1]}. A stored entry is listed in the index under the moment it expires by the clock of
 * Redis, or under {@code +inf} when it does not expire, so the index and the entry always expire together.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class RedisScript {
	/** Stores {@code value} for {@code ttl} milliseconds, or without expiry when {@code ttl} is not positive. */
	private static final String STORE = """
			local function store(value, ttl)
				if ttl > 0 then
					local time = redis.call('TIME')
					local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
					redis.call('SET', KEYS[1], value, 'PX', ttl)
					redis.call('ZADD', KEYS[2], string.format('%.0f', now + ttl), ARGV[1])
				else
					redis.call('SET', KEYS[1], value)
					redis.call('ZADD', KEYS[2], '+inf', ARGV[1])
				end
			end
			""";

	/** Stores {@code ARGV[2]} for {@code ARGV[3]} milliseconds. */
	static final RedisScript PUT = of(STORE + """
			store(ARGV[2], tonumber(ARGV[3]))
			return 1
			""");

	/**
	 * Stores like {@link #PUT}, but only while the entry holds {@code ARGV[5]} ({@code ARGV[4]} is {@code 1}) or
	 * nothing ({@code ARGV[4]} is {@code 0}).
	 */
	static final RedisScript COMPARE_AND_PUT = of(STORE + """
			local current = redis.call('GET', KEYS[1])
			if ARGV[4] == '1' then
				if current ~= ARGV[5] then return 0 end
			elseif current then
				return 0
			end
			store(ARGV[2], tonumber(ARGV[3]))
			return 1
			""");

	/** Removes the entry and its index member only while the entry holds {@code ARGV[2]}. */
	static final RedisScript COMPARE_AND_DELETE = of("""
			if redis.call('GET', KEYS[1]) ~= ARGV[2] then return 0 end
			redis.call('DEL', KEYS[1])
			redis.call('ZREM', KEYS[2], ARGV[1])
			return 1
			""");

	private final byte[] source;
	private final byte[] digest;

	private static @NotNull RedisScript of(@NotNull String source) {
		byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
		try {
			byte[] sha = MessageDigest.getInstance("SHA-1").digest(bytes);
			return new RedisScript(bytes, HexFormat.of().formatHex(sha).getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-1 is not available", e);
		}
	}

	/**
	 * Runs the script.
	 *
	 * @return {@code true} when the script returned {@code 1}, which every script does when it changed the entry
	 */
	boolean run(@NotNull Jedis jedis, @NotNull List<byte[]> keys, @NotNull List<byte[]> arguments) {
		Object result;
		try {
			result = jedis.evalsha(digest, keys, arguments);
		} catch (JedisNoScriptException missing) {
			jedis.scriptLoad(source);
			result = jedis.evalsha(digest, keys, arguments);
		}

		return Long.valueOf(1L).equals(result);
	}
}
