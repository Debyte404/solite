package solite.core;

import java.io.IOException;

public class UnreadableFolderException extends IOException {
    public UnreadableFolderException(String message) {
        super(message);
    }
    
    public UnreadableFolderException(String message, Throwable cause) {
        super(message, cause);
    }
}
