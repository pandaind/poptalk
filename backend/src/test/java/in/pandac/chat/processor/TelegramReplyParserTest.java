package in.pandac.chat.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.telegram.model.IncomingMessage;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramReplyParserTest {

    private final TelegramReplyParser parser = new TelegramReplyParser();

    @Test
    void extractsTheSessionIdAndStripsTheTagFromTheReplyText() {
        Exchange exchange = exchangeWithText("[SID:a1b2c3d4] Thanks for reaching out!");

        parser.process(exchange);

        verify(exchange.getIn()).setHeader("sessionId", "a1b2c3d4");
        verify(exchange.getIn()).setHeader("replyText", "Thanks for reaching out!");
    }

    @Test
    void leavesHeadersUntouchedWhenThereIsNoSidTag() {
        Exchange exchange = exchangeWithText("Just a regular admin message");

        parser.process(exchange);

        verify(exchange.getIn(), never()).setHeader(any(), any());
    }

    @Test
    void doesNothingWhenTheMessageHasNoText() {
        Exchange exchange = exchangeWithText(null);

        parser.process(exchange);

        verify(exchange.getIn(), never()).setHeader(any(), any());
    }

    private static Exchange exchangeWithText(String text) {
        IncomingMessage incoming = new IncomingMessage();
        incoming.setText(text);
        Message message = mock(Message.class);
        when(message.getBody(IncomingMessage.class)).thenReturn(incoming);
        Exchange exchange = mock(Exchange.class);
        when(exchange.getIn()).thenReturn(message);
        return exchange;
    }
}
