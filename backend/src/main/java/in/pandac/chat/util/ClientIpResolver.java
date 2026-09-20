package in.pandac.chat.util;

/**
 * The client-IP fallback chain used everywhere PopTalk rate-limits by IP:
 * prefer proxy headers when present, but always fall back to the real TCP
 * socket address, which cannot be spoofed by the client. Pulled out into one
 * place since it was being copy-pasted per entry point (contact registration,
 * chat messages, and now the streaming chat endpoint).
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(String realIpHeader, String forwardedForHeader, String servletRemoteAddr) {
        if (realIpHeader != null && !realIpHeader.isBlank()) {
            return realIpHeader;
        }
        if (forwardedForHeader != null && !forwardedForHeader.isBlank()) {
            return forwardedForHeader;
        }
        return servletRemoteAddr;
    }
}
