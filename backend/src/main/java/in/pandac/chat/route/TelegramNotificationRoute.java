package in.pandac.chat.route;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

/**
 * Decouples sending the admin Telegram notification from the HTTP request
 * that triggered it. Every caller enqueues onto {@code seda:telegramNotify}
 * (fire-and-forget — {@code waitForTaskToComplete=Never}) instead of calling
 * {@code telegram:bots} directly inline in an HTTP-triggered route.
 *
 * <p>This exists because of a real, reproduced bug: calling {@code telegram:bots}
 * directly from a route exposed via {@code platform-http} could race Tomcat's
 * async request completion against the route finishing on another thread,
 * leaving the visitor with an empty 500 instead of their actual response —
 * regardless of whether the Telegram call itself succeeded or failed, so
 * wrapping it in {@code doTry/doCatch} did not help. This is
 * <a href="https://issues.apache.org/jira/browse/CAMEL-24811">CAMEL-24811</a>
 * (dating back to Camel 3.7), fixed on the {@code camel-4.22.x} branch but not
 * yet in a released version as of Camel 4.22.1. Routing the Telegram send
 * through this separate consumer route sidesteps the race entirely — the
 * visitor-facing route never waits on or otherwise depends on Telegram's
 * completion — which is the correct design anyway: an admin notification
 * failing must never affect the visitor's response.
 */
@Component
public class TelegramNotificationRoute extends RouteBuilder {

    public static final String ENDPOINT = "seda:telegramNotify?waitForTaskToComplete=Never";

    @Override
    public void configure() {
        from(ENDPOINT)
                .routeId("telegram-notify-async")
                .to("telegram:bots");
    }
}
