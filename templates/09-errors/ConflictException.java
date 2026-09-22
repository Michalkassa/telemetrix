// TEMPLATE 09b — Another domain exception (a business rule was broken)
// Where it goes: src/main/java/com/telemetrix/common/ConflictException.java

package com.telemetrix.library;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
