package com.ocoelho.webscoket;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * Appender customizado do Log4j2 que envia cada evento de log para todos os clientes WebSocket conectados. Observacoes importantes: - Este plugin é descoberto porque informamos o pacote no
 * log4j2-spring.xml (atributo "packages"). - Caso o 'layout' não seja informado, aplicamos um PatternLayout padrão. Referencias sobre criação de Appender customizado e sistema de Plugins do Log4j2: -
 * Baeldung (exemplo de appender customizado) - Documentação oficial de Plugins do Log4j2
 */
@Plugin(name = "WebSocketAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE, printObject = true)
public class WebSocketLogAppender extends AbstractAppender {

    protected WebSocketLogAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions) {
        super(name, filter, layout, ignoreExceptions, Property.EMPTY_ARRAY);
    }

    @PluginFactory
    public static WebSocketLogAppender createAppender(@PluginAttribute("name") final String name, @PluginElement("Filter") final Filter filter,
            @PluginElement("Layout") Layout<? extends Serializable> layout) {
        if (name == null || name.isBlank()) {
            LOGGER.error("Nome nao fornecido para WebSocketLogAppender.");
            return null;
        }
        if (layout == null) {
            layout = PatternLayout.createDefaultLayout();
        }
        return new WebSocketLogAppender(name, filter, layout, true);
    }

    @Override
    public void append(LogEvent event) {
        try {
            byte[] bytes = getLayout().toByteArray(event);
            String formatted = new String(bytes, StandardCharsets.UTF_8);
            // Envia a linha formatada aos assinantes WebSocket
            LogStreamSessions.broadcast(formatted);
        } catch (Exception ex) {
            // Em caso de falha no envio para WS, respeitamos ignoreExceptions=true
            // para não interromper o fluxo de logging do aplicativo.
            LOGGER.debug("Falha ao transmitir log via WebSocket: {}", ex.getMessage());
        }
    }
}
