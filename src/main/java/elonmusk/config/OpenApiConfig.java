package elonmusk.config;

import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.info.License;
import org.eclipse.microprofile.openapi.annotations.servers.Server;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.ws.rs.core.Application;

@OpenAPIDefinition(
    info = @Info(
        title = "ElonMusk Investment Platform API",
        version = "1.0.0",
        description = "Comprehensive investment platform API for managing users, investments, deposits, withdrawals, and more.",
        contact = @Contact(
            name = "ElonMusk Investment Support",
            email = "support@elonmusk.com",
            url = "https://elonmusk.com/support"
        ),
        license = @License(
            name = "Proprietary",
            url = "https://elonmusk.com/license"
        )
    ),
    servers = {
        @Server(url = "http://localhost:8080", description = "Development Server"),
        @Server(url = "https://api.elonmusk.com", description = "Production Server")
    },
    tags = {
        @Tag(name = "Authentication", description = "User authentication and authorization"),
        @Tag(name = "Users", description = "User management operations"),
        @Tag(name = "Investments", description = "Investment products and user investments"),
        @Tag(name = "Transactions", description = "Financial transactions and wallet operations"),
        @Tag(name = "Admin", description = "Administrative operations"),
        @Tag(name = "Health", description = "Application health and monitoring")
    }
)
public class OpenApiConfig extends Application {
}