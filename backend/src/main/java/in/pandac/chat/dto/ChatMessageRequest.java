package in.pandac.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChatMessageRequest {

    @NotBlank(message = "Message cannot be blank")
    @Size(max = 2000, message = "Message must not exceed 2000 characters")
    private String message;

    private String timestamp;
    private UserInfo userInfo;

    public ChatMessageRequest() {}

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public UserInfo getUserInfo() { return userInfo; }
    public void setUserInfo(UserInfo userInfo) { this.userInfo = userInfo; }

    public static class UserInfo {
        private String name;
        private String contactType;
        
        public UserInfo() {}
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getContactType() { return contactType; }
        public void setContactType(String contactType) { this.contactType = contactType; }
    }
}
