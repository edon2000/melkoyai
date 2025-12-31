package elonmusk.controller;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.inject.Inject;
import io.agroal.api.AgroalDataSource;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.eclipse.microprofile.health.Readiness;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Path("/health")
public class HealthController {
    
    @Inject
    AgroalDataSource dataSource;
    
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now());
        health.put("service", "elonmusk-app");
        health.put("version", "1.0.0");
        
        // Database health check
        try (Connection conn = dataSource.getConnection()) {
            health.put("database", "UP");
        } catch (Exception e) {
            health.put("database", "DOWN");
            health.put("status", "DOWN");
        }
        
        return Response.ok(health).build();
    }
    
    @GET
    @Path("/ready")
    @Produces(MediaType.APPLICATION_JSON)
    public Response ready() {
        Map<String, Object> readiness = new HashMap<>();
        readiness.put("status", "READY");
        readiness.put("timestamp", LocalDateTime.now());
        
        return Response.ok(readiness).build();
    }
    
    @GET
    @Path("/detailed")
    @Produces(MediaType.APPLICATION_JSON)
    public Response detailedHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("timestamp", LocalDateTime.now());
        health.put("version", "1.0.0");
        
        // System metrics
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> system = new HashMap<>();
        system.put("totalMemory", runtime.totalMemory());
        system.put("freeMemory", runtime.freeMemory());
        system.put("maxMemory", runtime.maxMemory());
        system.put("processors", runtime.availableProcessors());
        health.put("system", system);
        
        // Database metrics
        Map<String, Object> database = new HashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            database.put("status", "UP");
            
            try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM users");
                 ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    database.put("userCount", rs.getInt(1));
                }
            }
            
        } catch (Exception e) {
            database.put("status", "DOWN");
            database.put("error", e.getMessage());
        }
        health.put("database", database);
        
        boolean isHealthy = "UP".equals(((Map<?, ?>) health.get("database")).get("status"));
        health.put("status", isHealthy ? "UP" : "DOWN");
        
        return Response.ok(health).build();
    }
}

@Liveness
class LivenessCheck implements HealthCheck {
    @Override
    public HealthCheckResponse call() {
        return HealthCheckResponse.up("Application is running");
    }
}

@Readiness
class ReadinessCheck implements HealthCheck {
    
    @Inject
    AgroalDataSource dataSource;
    
    @Override
    public HealthCheckResponse call() {
        try (Connection conn = dataSource.getConnection()) {
            return HealthCheckResponse.up("Database connection is ready");
        } catch (Exception e) {
            return HealthCheckResponse.down("Database connection failed: " + e.getMessage());
        }
    }
}