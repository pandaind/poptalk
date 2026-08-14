package in.pandac.chat.dto;

public class ChatMessageResponse {
    private String messageId;
    private String timestamp;
    private String response;

    public ChatMessageResponse() {}

    public ChatMessageResponse(String messageId, String timestamp, String response) {
        this.messageId = messageId;
        this.timestamp = timestamp;
        this.response = response;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }
}
