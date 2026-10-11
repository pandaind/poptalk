package in.pandac.chat.route;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class TelegramNotificationRouteTest {

    @Test
    void withoutABotTokenNotificationsAreDroppedAndTelegramIsNeverContacted() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new TelegramNotificationRoute(""));
            context.start();
            ProducerTemplate producer = context.createProducerTemplate();

            assertThatCode(() -> producer.sendBody(TelegramNotificationRoute.ENDPOINT, "hello"))
                    .doesNotThrowAnyException();

            assertThat(context.getRoute("telegram-notify-async")).isNotNull();
            // The telegram component was never needed: no endpoint for it was created.
            assertThat(context.hasEndpoint("telegram:bots")).isNull();
        }
    }

    @Test
    void aBlankTokenCountsAsNotConfigured() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addRoutes(new TelegramNotificationRoute("   "));
            context.start();

            assertThat(context.hasEndpoint("telegram:bots")).isNull();
        }
    }
}
