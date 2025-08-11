package com.ocoelho.webscoket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.ocoelho.properties.AppLoggingProperties;

@Configuration
@EnableWebSocket
public class LogWebSocketConfig implements WebSocketConfigurer {

    private final LogStreamWebSocketHandler logStreamWebSocketHandler;
    private final AppLoggingProperties appLoggingProperties;

    public LogWebSocketConfig(LogStreamWebSocketHandler logStreamWebSocketHandler, AppLoggingProperties appLoggingProperties) {
        this.logStreamWebSocketHandler = logStreamWebSocketHandler;
        this.appLoggingProperties = appLoggingProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Mapeia o WS e libera as origens conforme configurado
        registry.addHandler(logStreamWebSocketHandler, "/ws/logs").setAllowedOrigins(appLoggingProperties.getAllowedOrigins().toArray(new String[0]));
    }
}
