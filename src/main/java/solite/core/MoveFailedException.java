package solite.core;

import java.io.IOException;

public class MoveFailedException extends IOException {
    public MoveFailedException(String message) {
        super(message);
    }
    
    public MoveFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
