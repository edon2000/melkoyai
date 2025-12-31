package elonmusk.dto;

import jakarta.ws.rs.FormParam;

public class LoginFormRequest {
    @FormParam("account") 
    public String account;
    
    @FormParam("password") 
    public String password;
    
    @FormParam("rememberMe") 
    public boolean rememberMe = false;
}