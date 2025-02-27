package org.ergoplatform.appkit;

/**
 * Contains unsigned transaction augmented with one `ReductionResult` for each
 * `UnsignedInput`.
 * Can be obtained by reducing an unsigned transaction.
 */
public interface ReducedTransaction {
    /**
     * Returns the underlying reduced transaction data.
     */
    ReducedErgoLikeTransaction getTx();

    /**
     * Returns the cost accumulated while reducing the original unsigned
     * transaction.
     */
    int getCost();

    /**
     * Returns the serialized bytes of this transaction.
     */
    byte[] toBytes();
}
