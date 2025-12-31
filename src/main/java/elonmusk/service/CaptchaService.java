package elonmusk.service;

import elonmusk.config.AppExecutor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;

@ApplicationScoped
public class CaptchaService {
    
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, String> captchaStore = new ConcurrentHashMap<>();
    
    @Inject
    @AppExecutor
    ScheduledExecutorService scheduledExecutorService;
    
    public void init() {
        // Clean expired captchas every 5 minutes
        scheduledExecutorService.scheduleAtFixedRate(
            captchaStore::clear, 5, 5, TimeUnit.MINUTES
        );
    }
    
    public CaptchaData generateCaptcha() {
        String captchaText = generateRandomText();
        String sessionId = generateSessionId();
        String imageBase64 = createCaptchaImage(captchaText);
        
        captchaStore.put(sessionId, captchaText.toLowerCase());
        
        return new CaptchaData(sessionId, imageBase64);
    }
    
    public boolean validateCaptcha(String sessionId, String userInput) {
        if (sessionId == null || userInput == null) return false;
        
        String storedCaptcha = captchaStore.remove(sessionId);
        return storedCaptcha != null && storedCaptcha.equals(userInput.toLowerCase().trim());
    }
    
    private String generateRandomText() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
    
    private String generateSessionId() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    private String createCaptchaImage(String text) {
        int width = 200, height = 60;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        // Background
        g.setColor(new Color(240, 240, 240));
        g.fillRect(0, 0, width, height);
        
        // Add noise lines
        g.setColor(new Color(200, 200, 200));
        for (int i = 0; i < 8; i++) {
            g.drawLine(random.nextInt(width), random.nextInt(height), 
                      random.nextInt(width), random.nextInt(height));
        }
        
        // Draw text with random fonts and colors
        Font[] fonts = {
            new Font("Arial", Font.BOLD, 24),
            new Font("Times", Font.BOLD, 26),
            new Font("Courier", Font.BOLD, 22)
        };
        
        int x = 20;
        for (int i = 0; i < text.length(); i++) {
            g.setFont(fonts[random.nextInt(fonts.length)]);
            g.setColor(new Color(random.nextInt(100), random.nextInt(100), random.nextInt(100)));
            
            // Random rotation
            double angle = (random.nextDouble() - 0.5) * 0.5;
            g.rotate(angle, x, 35);
            g.drawString(String.valueOf(text.charAt(i)), x, 35);
            g.rotate(-angle, x, 35);
            
            x += 25 + random.nextInt(10);
        }
        
        g.dispose();
        
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to generate captcha image", e);
        }
    }
    
    public static class CaptchaData {
        public final String sessionId;
        public final String imageBase64;
        
        public CaptchaData(String sessionId, String imageBase64) {
            this.sessionId = sessionId;
            this.imageBase64 = imageBase64;
        }
    }
}