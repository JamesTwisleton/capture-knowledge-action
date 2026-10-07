package com.cka.core;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.util.UUID;

/**
 * Mints the id for an action decision chain, and reads the capture time back out of one.
 *
 * <p>These are <strong>UUIDv7</strong> (RFC 9562), not the JDK's random v4: the leading 48 bits
 * hold the Unix timestamp in milliseconds, so ids sort into the order their chains began and the
 * id alone says when that was. Two later tickets lean on that. T14 has to list a chain's steps and
 * the activity view's chains in time order, which an ordinary sort now gives for free; and T10
 * stores these as primary keys, where a random id scatters inserts across the index instead of
 * appending to the end of it.
 *
 * <p>Sorting matches generation order lexically as well as numerically, which is what makes
 * {@code ORDER BY chain_id} work whether the column ends up text or binary.
 */
public final class ChainId {

    /** Thread-safe, and the per-millisecond counter that keeps ids unique lives here, so share it. */
    private static final TimeBasedEpochGenerator GENERATOR = Generators.timeBasedEpochGenerator();

    private ChainId() {}

    public static UUID next() {
        return GENERATOR.generate();
    }

    /** The millisecond the id was minted, read out of its first 48 bits. */
    public static long capturedAtEpochMilli(UUID chainId) {
        return chainId.getMostSignificantBits() >>> 16;
    }
}
