package elonmusk.controller;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.InputStream;
import java.io.IOException;
import java.util.logging.Logger;

@Path("/images")
public class StaticResourceController {
    
    private static final Logger logger = Logger.getLogger(StaticResourceController.class.getName());
    
    @GET
    @Path("/{filename}")
    public Response getImage(@PathParam("filename") String filename) {
        try {
            // Try to load the image from resources
            InputStream imageStream = getClass().getClassLoader()
                    .getResourceAsStream("META-INF/resources/images/" + filename);
            
            if (imageStream == null) {
                // Fallback to wifi.jpg if image not found
                imageStream = getClass().getClassLoader()
                        .getResourceAsStream("META-INF/resources/images/wifi.jpg");
                
                if (imageStream == null) {
                    return Response.status(Response.Status.NOT_FOUND).build();
                }
            }
            
            // Determine content type based on file extension
            String contentType = getContentType(filename);
            
            final InputStream finalImageStream = imageStream;
            StreamingOutput stream = output -> {
                try {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = finalImageStream.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                } catch (IOException e) {
                    logger.warning("Error streaming image: " + e.getMessage());
                } finally {
                    try {
                        finalImageStream.close();
                    } catch (IOException e) {
                        logger.warning("Error closing image stream: " + e.getMessage());
                    }
                }
            };
            
            return Response.ok(stream, contentType)
                    .header("Cache-Control", "public, max-age=3600")
                    .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                    .build();
                    
        } catch (Exception e) {
            logger.severe("Error serving image " + filename + ": " + e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    private String getContentType(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        switch (extension) {
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "svg":
                return "image/svg+xml";
            case "avif":
                return "image/avif";
            case "webp":
                return "image/webp";
            default:
                return "image/jpeg";
        }
    }
}