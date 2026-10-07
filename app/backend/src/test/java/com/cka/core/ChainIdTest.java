package com.cka.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * The two properties T10 and T14 will rely on: chain ids sort into the order they were minted,
 * and the time they were minted can be read back out of them.
 */
class ChainIdTest {

    @Test
    void idsSortIntoTheOrderTheyWereMinted() {
        var minted = new ArrayList<UUID>();
        for (var i = 0; i < 1_000; i++) {
            minted.add(ChainId.next());
        }

        var shuffled = new ArrayList<>(minted);
        Collections.shuffle(shuffled);

        // Sorted as text, because that is how a database will sort the column whether it ends up
        // stored as a string or as bytes. Java's UUID.compareTo would not prove the same thing:
        // it compares the halves as *signed* longs, so it happens to agree only while the
        // timestamp's top bit is still clear.
        shuffled.sort(Comparator.comparing(UUID::toString));
        assertThat(shuffled).containsExactlyElementsOf(minted);
    }

    @Test
    void theMintingTimeCanBeReadBackOutOfTheId() {
        var before = Instant.now().toEpochMilli();
        var chainId = ChainId.next();
        var after = Instant.now().toEpochMilli();

        assertThat(ChainId.capturedAtEpochMilli(chainId)).isBetween(before, after);
    }

    @Test
    void idsAreVersion7AndStillUnique() {
        var ids = List.of(ChainId.next(), ChainId.next(), ChainId.next());

        assertThat(ids).extracting(UUID::version).containsOnly(7);
        assertThat(ids).doesNotHaveDuplicates();
    }
}
