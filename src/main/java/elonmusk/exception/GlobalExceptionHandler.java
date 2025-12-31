package elonmusk.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.sql.SQLException;
import java.net.URI;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Global exception handler that prevents information disclosure
 * Logs errors but returns generic error messages to users
 * DISABLED for gift-related requests to prevent unwanted redirects
 */
@Provider
public class GlobalExceptionHandler implements ExceptionMapper<Exception> {
    
    private static final Logger LOGGER = Logger.getLogger(GlobalExceptionHandler.class.getName());
    
    @Override
    public Response toResponse(Exception exception) {
        // Handle WebApplicationException (redirects) silently
        if (exception instanceof WebApplicationException) {
            WebApplicationException wae = (WebApplicationException) exception;
            Response originalResponse = wae.getResponse();
            if (originalResponse != null && 
                (originalResponse.getStatus() == 302 || originalResponse.getStatus() == 303)) {
                return originalResponse;
            }
        }
        
        // Handle NotFoundException silently
        if (exception instanceof jakarta.ws.rs.NotFoundException) {
            LOGGER.fine("Resource not found: " + exception.getMessage());
            return Response.seeOther(URI.create("/login")).build();
        }
        
        // Log the exception with full details for debugging
        LOGGER.log(Level.SEVERE, "Unhandled exception occurred", exception);
        
        // Check if this is a gift-related request - DO NOT HANDLE, let it pass through
        StackTraceElement[] stackTrace = exception.getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            String methodName = element.getMethodName();
            if ((className.contains("gift") || className.contains("Gift")) ||
                (methodName.contains("gift") || methodName.contains("Gift"))) {
                LOGGER.info("Gift-related exception detected - NOT handling to prevent redirects");
                // Return a 500 error instead of redirect to prevent GlobalExceptionHandler interference
                return Response.status(500).entity("Internal Server Error").build();
            }
        }
        
        // Return generic error message to user (no sensitive information)
        return Response.seeOther(URI.create("/login?error=Service+unavailable")).build();
    }
}