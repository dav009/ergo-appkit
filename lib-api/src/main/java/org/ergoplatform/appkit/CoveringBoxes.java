package org.ergoplatform.appkit;

import java.util.List;

/**
 * Represents a collection of boxes covering the given amount of NanoErgs to spend.
 * If the amount is not covered in full, then isCovered() returns false. Tokens are not taken into
 * account.
 * Thus, this class allows to represent partial coverage, which is useful to collect boxes
 * in many steps.
 */
public class CoveringBoxes {
    private final long _amountToSpend;
    private final List<InputBox> _boxes;

    public CoveringBoxes(long amountToSpend, List<InputBox> boxes) {
        _amountToSpend = amountToSpend;
        _boxes = boxes;
    }

    /** The amount covered by the boxes in this set. */
    public long getCoveredAmount() {
        long value = 0;
        for (InputBox box : _boxes) {
            value = value + box.getValue();
        }
        return value;
    }

    /** Returns true if the amount is covered by the boxes in this set, false otherwise. */
    public boolean isCovered() { return getCoveredAmount() >= _amountToSpend; }

    /** Returns a list of boxes stored in this set. */
    public List<InputBox> getBoxes() {
        return _boxes;
    }
}
