package com.zify.common.persistence.id;

import java.util.UUID;

/**
 * ID generator utilities.
 */
public final class IdGenerator {

    private IdGenerator() {
    }

    public static String uuid() {
        return UUID.randomUUID().toString();
    }
}
