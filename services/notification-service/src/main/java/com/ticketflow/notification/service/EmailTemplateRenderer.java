package com.ticketflow.notification.service;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Renders Thymeleaf HTML email bodies. Templates use table-based layout and
 * inline styles on purpose — email clients have no meaningful CSS support and
 * strip &lt;style&gt; blocks.
 */
@Service
public class EmailTemplateRenderer {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy 'at' h:mm a").withZone(ZoneId.systemDefault());

    private final TemplateEngine templateEngine;

    public EmailTemplateRenderer(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    public String render(String template, Map<String, Object> variables) {
        Context context = new Context();
        variables.forEach(context::setVariable);
        return templateEngine.process("email/" + template, context);
    }

    public static String formatInstant(Instant instant) {
        return instant == null ? "TBC" : FORMATTER.format(instant);
    }
}
