# `com.cka.core` — domain model tests

Tests for [`main/.../core`](../../../../../main/java/com/cka/core/AGENTS.md). Most of that package
is plain records with nothing to test — a record's accessors and `equals` are the compiler's work,
not ours, and testing them would be testing Java.

`ChainIdTest` is the exception, because `ChainId` makes two promises the rest of the system will
build on: ids sort into the order they were minted, and the minting time can be read back out of
one. T10 stores them as primary keys and T14 lists chains in time order, so both are contracts,
not incidental behaviour of whichever library is underneath.

- **Assert the property, not the implementation.** The sort test sorts *as text*, because that is
  how a database orders the column. `UUID.compareTo` compares the halves as signed longs and only
  happens to agree while the timestamp's top bit is clear — it would pass without proving the
  thing that matters.
- **A new test here needs the same justification**: a promise something else depends on. If it
  would only fail when the JDK or a library is broken, it is not earning its runtime.
