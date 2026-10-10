package in.pandac.chat.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class ContactValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "ada@example.com", "ada.lovelace+chat@sub.example.co.uk", "o'brien@example.org",
            "a@b.io", "  padded@example.com  "})
    void acceptsRealLookingEmailAddresses(String email) {
        assertThat(ContactValidator.isValid("email", email)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "", "plainaddress", "no-at-sign.example.com", "@example.com", "ada@", "ada@@example.com",
            "ada@example", "ada@.com", "ada@example..com", "ada lovelace@example.com", "ada@-bad.com",
            "ada@example.c", "<script>@example.com",
            // the placeholder older widgets invented, and other reserved names
            "visitor-1b4e28ba-2fa1-11d2-883f-0016d3cca427@widget.local",
            "x@host.localhost", "x@mail.invalid", "x@site.test", "x@nowhere.example", "x@printer.lan"})
    void rejectsThingsThatCannotBeAnEmailAddress(String email) {
        assertThat(ContactValidator.isValid("email", email)).isFalse();
    }

    @Test
    void rejectsAnOverlongEmail() {
        String local = "a".repeat(64);
        String domain = ("b".repeat(60) + ".").repeat(4) + "com";
        assertThat(ContactValidator.isValid("email", local + "@" + domain)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"+14155550123", "4155550123", "+44 20 7946 0958", "(415) 555-0123", "415.555.0123"})
    void acceptsPhoneNumbersInCommonFormats(String phone) {
        assertThat(ContactValidator.isValid("phone", phone)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "123", "abcdefghij", "+1 415 555 0123 ext 5", "1234567890123456", "++14155550123", "call me"})
    void rejectsThingsThatCannotBeAPhoneNumber(String phone) {
        assertThat(ContactValidator.isValid("phone", phone)).isFalse();
    }

    @Test
    void theTypeDecidesWhichRuleApplies() {
        assertThat(ContactValidator.isValid("email", "+14155550123")).isFalse();
        assertThat(ContactValidator.isValid("phone", "ada@example.com")).isFalse();
        assertThat(ContactValidator.isValid("fax", "ada@example.com")).isFalse();
        assertThat(ContactValidator.isValid(null, "ada@example.com")).isFalse();
        assertThat(ContactValidator.isValid("email", null)).isFalse();
    }
}
