// TEMPLATE 09a — Your own exceptions
// Where it goes: src/main/java/com/telemetrix/common/NotFoundException.java  (shared by all features)
//
// Services throw meaningful exceptions; ONE central handler (09c) turns them into HTTP responses.
// Services therefore stay free of HTTP concepts.
// RuntimeException = "unchecked": no `throws` clause needed, and it rolls back @Transactional.

package com.telemetrix.library;

public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Object id) {
        super(what + " " + id + " not found");
    }
}
