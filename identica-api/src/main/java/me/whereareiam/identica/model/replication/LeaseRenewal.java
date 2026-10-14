package me.whereareiam.identica.model.replication;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * One renewal of a leased record, handed to {@link me.whereareiam.identica.replication.lease.LeasedRecords#write}.
 */
@Getter
@ToString
@RequiredArgsConstructor
public final class LeaseRenewal {
	/**
	 * How many times the record has been renewed since this proxy started holding it, this renewal included, so
	 * the first renewal is {@code 1}. Records that keep further entries renew them every so many renewals.
	 */
	private final int count;
	/**
	 * Whether the record was gone from the shared store and is written again from this proxy's copy.
	 */
	private final boolean restored;
	/**
	 * How long the record lives after this write, in milliseconds: the lease lifetime, or less when the lease ends
	 * sooner.
	 */
	private final long ttlMs;
}
