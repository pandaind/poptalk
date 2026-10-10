package in.pandac.chat.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Format checks for the contact a visitor gives before chatting.
 *
 * <p>This is syntactic validation: it rejects values that cannot be a real email address or phone
 * number, including the throwaway placeholders older widgets used to invent. It does not prove the
 * visitor owns the address; that would need a confirmation message.
 */
public final class ContactValidator {

    private static final int MAX_EMAIL_LENGTH = 254; // RFC 5321 path limit
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+'-]{1,64}@([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]{2,}$");
    /** E.164 allows at most 15 digits; 7 is the shortest real numbers get. */
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,15}$");
    /** Top-level names reserved for local or documentation use (RFC 2606 / 6761): never real. */
    private static final Set<String> RESERVED_TLDS =
            Set.of("local", "localhost", "invalid", "test", "example", "internal", "lan", "home");

    private ContactValidator() {
    }

    /** {@code type} is {@code "email"} or {@code "phone"}. Anything else is invalid. */
    public static boolean isValid(String type, String contact) {
        if (type == null || contact == null) {
            return false;
        }
        String value = contact.trim();
        return switch (type) {
            case "email" -> isValidEmail(value);
            case "phone" -> isValidPhone(value);
            default -> false;
        };
    }

    public static boolean isValidEmail(String value) {
        if (value == null || value.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(value).matches()) {
            return false;
        }
        String tld = value.substring(value.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return !RESERVED_TLDS.contains(tld);
    }

    public static boolean isValidPhone(String value) {
        if (value == null) {
            return false;
        }
        // Spaces, dashes, dots and brackets are formatting, not part of the number.
        String digits = value.replaceAll("[\\s\\-.()]", "");
        return PHONE.matcher(digits).matches();
    }
}
