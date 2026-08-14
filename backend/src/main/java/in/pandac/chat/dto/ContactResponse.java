package in.pandac.chat.dto;

public class ContactResponse {
    private String token;
    private String sessionId;
    private String message;

    public ContactResponse() {}

    public ContactResponse(String token, String sessionId, String message) {
        this.token = token;
        this.sessionId = sessionId;
        this.message = message;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
