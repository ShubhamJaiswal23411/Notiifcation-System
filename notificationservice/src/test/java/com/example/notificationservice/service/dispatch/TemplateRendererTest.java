package com.example.notificationservice.service.dispatch;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

public class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void replacesSinglePlaceholder() {
        String result = renderer.render("Hi {{name}}", Map.of("name", "Alex"));
        assertThat(result).isEqualTo("Hi Alex");
    }

    @Test
    void replacesMultiplePlaceholders() {
        String result = renderer.render("Order #{{orderId}} for {{name}}", Map.of("orderId", "1001", "name", "Sam"));
        assertThat(result).isEqualTo("Order #1001 for Sam");
    }

    @Test
    void missingVariableBecomesEmptyString() {
        String result = renderer.render("Hi {{name}}", Map.of());
        assertThat(result).isEqualTo("Hi ");
    }

    @Test
    void nullVariablesMapDoesNotThrow() {
        String result = renderer.render("Hi {{name}}", null);
        assertThat(result).isEqualTo("Hi ");
    }

    @Test
    void nullTemplateReturnsNull() {
        assertThat(renderer.render(null, Map.of("name", "Alex"))).isNull();
    }

    @Test
    void templateWithNoPlaceholdersIsUnchanged() {
        String result = renderer.render("Plain text, no variables", Map.of("unused", "x"));
        assertThat(result).isEqualTo("Plain text, no variables");
    }

    @Test
    void handlesRepeatedPlaceholder() {
        String result = renderer.render("{{name}}, hi {{name}}!", Map.of("name", "Alex"));
        assertThat(result).isEqualTo("Alex, hi Alex!");
    }
}