package com.ocoelho.webscoket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Handler WebSocket responsável por gerenciar o ciclo de vida das conexões.
 * Assim que a conexão é estabelecida, a sessão é registrada e passa a receber logs em tempo real.
 * Caso uma mensagem de texto chegue do cliente, ignoramos (o fluxo aqui é "server push").
 */
@Component
public class LogStreamWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(LogStreamWebSocketHandler.class);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        LogStreamSessions.register(session);
        // Mensagem inicial opcional para confirmar conexão
        session.sendMessage(new TextMessage("Conexao de monitoramento de logs estabelecida com sucesso."));
        logger.info("Sessao WebSocket registrada para stream de logs. Id: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // Este handler não processa mensagens do cliente. O objetivo é somente enviar logs.
        // Podemos registrar ping/pong ou comandos futuros se necessário.
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.warn("Erro de transporte na sessao WebSocket de logs. Id: {}. Causa: {}", session.getId(), exception.getMessage());
        LogStreamSessions.unregister(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        logger.info("Sessao WebSocket encerrada. Id: {}. Status: {}", session.getId(), status);
        LogStreamSessions.unregister(session);
    }
}

