package com.raoulvdberge.refinedstorage.apiimpl.autocrafting.task;

enum ProcessingState {
    READY,
    MACHINE_NONE,
    MACHINE_DOES_NOT_ACCEPT,
    PROCESSED,
    LOCKED
}
