package elonmusk.dto;

import jakarta.ws.rs.FormParam;

public class RegisterFormRequest {
    @FormParam("name") 
    public String name;
    
    @FormParam("account") 
    public String account;
    
    @FormParam("email") 
    public String email;
    
    @FormParam("password") 
    public String password;
    
    @FormParam("passwordRepeat") 
    public String passwordRepeat;
    
    @FormParam("verificationCode") 
    public String verificationCode;
    
    @FormParam("referralCode") 
    public String referralCode;
}