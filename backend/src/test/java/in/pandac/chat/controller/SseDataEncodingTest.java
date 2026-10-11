package in.pandac.chat.controller;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SseDataEncodingTest {

    /** What an SSE client does with the data lines of one event (the WHATWG rules). */
    private static String read(String dataField) {
        StringBuilder out = new StringBuilder();
        String[] lines = dataField.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String value = lines[i];
            if (value.startsWith(" ")) {
                value = value.substring(1);
            }
            out.append(i > 0 ? "\n" : "").append(value);
        }
        return out.toString();
    }

    @Test
    void chunksSurviveAClientThatRemovesTheLeadingSpace() {
        List<String> chunks = List.of("Hello", " world", "  two spaces", "line one\n line two", "\nstarts on a new line", " ", "");
        for (String chunk : chunks) {
            assertThat(read(ChatStreamController.sseData(chunk))).as("chunk %s", chunk).isEqualTo(chunk);
        }
    }

    @Test
    void streamedTokensJoinBackIntoTheOriginalSentence() {
        StringBuilder sentence = new StringBuilder();
        for (String token : List.of("I'm", " not", " sure", " what", " you", " mean", ".")) {
            sentence.append(read(ChatStreamController.sseData(token)));
        }

        assertThat(sentence).hasToString("I'm not sure what you mean.");
    }
}
