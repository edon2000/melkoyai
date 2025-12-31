package elonmusk.dto;

import java.time.LocalDateTime;

public class ErrorResponse {
    public String error;
    public String message;
    public int status;
    public LocalDateTime timestamp;
    public String path;
    
    public ErrorResponse() {
        this.timestamp = LocalDateTime.now();
    }
    
    public ErrorResponse(String error, String message, int status, String path) {
        this();
        this.error = error;
        this.message = message;
        this.status = status;
        this.path = path;
    }
    
    public static ErrorResponse of(String error, String message, int status, String path) {
        return new ErrorResponse(error, message, status, path);
    }
}