package elonmusk.filter;

import elonmusk.config.SecurityConfig;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.logging.Logger;
import java.net.URI;

@Provider
public class SecurityRequestFilter implements ContainerRequestFilter {
    
    private static final Logger LOGGER = Logger.getLogger(SecurityRequestFilter.class.getName());
    
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String userAgent = requestContext.getHeaderString("User-Agent");
        String path = requestContext.getUriInfo().getPath();
        String userId = null;
        
        // Get user_id from cookies
        if (requestContext.getCookies() != null && requestContext.getCookies().containsKey("user_id")) {
            userId = requestContext.getCookies().get("user_id").getValue();
        }
        
        // Block malicious user agents
        if (SecurityConfig.isBlockedUserAgent(userAgent)) {
            LOGGER.warning("Blocked request from suspicious user agent: " + userAgent);
            requestContext.abortWith(Response.status(Response.Status.FORBIDDEN).build());
            return;
        }
        
        // Allow public paths without authentication
        if (isPublicPath(path)) {
            return;
        }
        
        // Allow controller-authenticated paths to handle their own authentication
        if (isControllerAuthenticatedPath(path)) {
            return;
        }
        
        // Check authentication for other protected paths
        if (userId == null || userId.trim().isEmpty()) {
            // Redirect to login for unauthenticated users
            requestContext.abortWith(Response.seeOther(URI.create("/login")).build());
            return;
        }
        
        // Enhanced security validation
        if (!SecurityUtil.isValidInput(path, 200)) {
            LOGGER.warning("Invalid or suspicious request path: " + SecurityUtil.sanitizeInput(path));
            requestContext.abortWith(Response.status(Response.Status.BAD_REQUEST).build());
            return;
        }
        
        // Log suspicious requests
        if (path.contains("..") || path.contains("script") || path.contains("eval")) {
            LOGGER.warning("Suspicious request path detected: " + SecurityUtil.sanitizeInput(path) + " from " + 
                SecurityUtil.sanitizeInput(requestContext.getHeaderString("X-Forwarded-For")));
        }
    }
    
    private boolean isPublicPath(String path) {
        return path.equals("/") || 
               path.equals("/login") || 
               path.equals("/signup") || 
               path.equals("/forgot-password") ||
               path.startsWith("/static/") ||
               path.startsWith("/images/") ||
               path.startsWith("/css/") ||
               path.startsWith("/js/") ||
               path.startsWith("/META-INF/") ||
               path.equals("/favicon.ico") ||
               path.startsWith("/health") ||
               path.startsWith("/million");
    }
    
    private boolean isControllerAuthenticatedPath(String path) {
        // These paths handle their own authentication in the controller
        return path.equals("/dashboard") ||
               path.equals("/home") ||
               path.equals("/gift") ||
               path.equals("/invite") ||
               path.equals("/purchase") ||
               path.equals("/my") ||
               path.equals("/product") ||
               path.equals("/team") ||
               path.equals("/help") ||
               path.equals("/service") ||
               path.equals("/income") ||
               path.equals("/recharge") ||
               path.equals("/withdrawals");
    }
}