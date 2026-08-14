package in.pandac.chat.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TelegramReplyParser implements Processor {

    // Matches "[SID:a1b2c3d4]" anywhere in the message
    private static final Pattern SID_PATTERN = Pattern.compile("\\[SID:([a-zA-Z0-9_-]+)]");

    @Override
    public void process(Exchange exchange) {
        IncomingMessage msg = exchange.getIn().getBody(IncomingMessage.class);
        String text = msg.getText();

        if (text == null) {
            return;
        }

        Matcher matcher = SID_PATTERN.matcher(text);
        if (matcher.find()) {
            String sessionId = matcher.group(1);
            // Remove the tag from the reply text before sending to visitor
            String cleanText = matcher.replaceFirst("").trim();

            exchange.getIn().setHeader("sessionId", sessionId);
            exchange.getIn().setHeader("replyText", cleanText);
        }
    }
}
