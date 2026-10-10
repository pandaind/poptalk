package in.pandac.chat.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ContactRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static ContactRequest request(String name, String contact, String type) {
        ContactRequest r = new ContactRequest();
        r.setName(name);
        r.setContact(contact);
        r.setContactType(type);
        return r;
    }

    private String problems(ContactRequest r) {
        return validator.validate(r).stream().map(v -> v.getMessage()).sorted().collect(Collectors.joining(" | "));
    }

    @Test
    void aNamedVisitorWithARealEmailIsAccepted() {
        assertThat(validator.validate(request("Ada Lovelace", "ada@example.com", "email"))).isEmpty();
        assertThat(validator.validate(request("Ada Lovelace", "+44 20 7946 0958", "phone"))).isEmpty();
    }

    @Test
    void theOldWidgetsPlaceholderIdentityIsRejected() {
        ContactRequest legacy = request("Website Visitor", "visitor-1b4e28ba@widget.local", "email");

        assertThat(problems(legacy)).contains("valid email address or phone number");
    }

    @Test
    void aContactThatDoesNotMatchItsTypeIsRejected() {
        assertThat(problems(request("Ada", "not-an-email", "email"))).contains("valid email address");
        assertThat(problems(request("Ada", "ada@example.com", "phone"))).contains("valid email address");
    }

    @Test
    void missingFieldsReportTheirOwnMessageRatherThanTheFormatOne() {
        String p = problems(request("Ada", "", "email"));

        assertThat(p).contains("Contact is required").doesNotContain("valid email address");
    }
}
