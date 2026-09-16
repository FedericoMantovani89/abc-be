package it.abc.musical.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlSanitizerTest {

    @Test
    void removesScriptTagsButKeepsAllowedFormatting() {
        String dirty = "<p>Trama <strong>bella</strong></p><script>alert('xss')</script>";

        String clean = HtmlSanitizer.sanitize(dirty);

        assertThat(clean).contains("<p>", "<strong>bella</strong>");
        assertThat(clean).doesNotContain("script", "alert");
    }

    @Test
    void keepsHttpLinksAndStripsJavascriptProtocol() {
        String clean = HtmlSanitizer.sanitize(
                "<a href=\"https://ok.it\">ok</a><a href=\"javascript:alert(1)\">bad</a>");

        assertThat(clean).contains("href=\"https://ok.it\"");
        assertThat(clean).doesNotContain("javascript:");
    }

    @Test
    void nullAndBlankPassThrough() {
        assertThat(HtmlSanitizer.sanitize(null)).isNull();
        assertThat(HtmlSanitizer.sanitize("  ")).isEqualTo("  ");
    }
}
