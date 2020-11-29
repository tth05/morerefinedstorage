package com.raoulvdberge.refinedstorage.item.wrench;

public enum WrenchMode {
    COVER(3, 1),
    COPY(0, 2),
    PASTE(1, 3),
    ROTATE(2, 0);

    private final int previous;
    private final int next;

    WrenchMode(int previous, int next) {
        this.previous = previous;
        this.next = next;
    }

    public WrenchMode getPrevious() {
        return WrenchMode.values()[previous];
    }

    public WrenchMode getNext() {
        return WrenchMode.values()[next];
    }
}
