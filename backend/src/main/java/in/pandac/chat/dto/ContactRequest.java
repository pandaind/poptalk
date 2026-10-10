package in.pandac.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import in.pandac.chat.util.ContactValidator;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class ContactRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be 2–100 characters")
    private String name;

    @NotBlank(message = "Contact is required")
    @Size(max = 255, message = "Contact must not exceed 255 characters")
    private String contact;

    @NotBlank(message = "contactType is required")
    @Pattern(regexp = "^(email|phone)$", message = "contactType must be 'email' or 'phone'")
    private String contactType;

    private String source;
    private String timestamp;
    private String userAgent;
    private String referrer;

    public ContactRequest() {}

    /**
     * The contact must really be an email address or phone number, matching {@code contactType}.
     * Skipped when either field is blank so the clearer "is required" message is the one shown.
     */
    @JsonIgnore
    @AssertTrue(message = "Contact must be a valid email address or phone number matching contactType")
    public boolean isContactValid() {
        if (contact == null || contact.isBlank() || contactType == null || contactType.isBlank()) {
            return true;
        }
        return ContactValidator.isValid(contactType, contact);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
    public String getContactType() { return contactType; }
    public void setContactType(String contactType) { this.contactType = contactType; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public String getUserAgent() { return userAgent; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public String getReferrer() { return referrer; }
    public void setReferrer(String referrer) { this.referrer = referrer; }
}
