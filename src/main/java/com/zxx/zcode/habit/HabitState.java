package com.zxx.zcode.habit;

/**
 * Lifecycle of a shortcut->intent mapping.
 */
public enum HabitState {
    NEW,
    SUGGEST_ONLY,
    AUTO_READY,
    AUTO_ACTIVE,
    DEGRADED
}
