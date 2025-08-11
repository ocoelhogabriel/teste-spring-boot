package com.ocoelho.webscoket;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Gerencia as sessões WebSocket conectadas e realiza o broadcast das mensagens de log.
 * Implementado de forma estática para ser acessível tanto pelo Handler quanto pelo Appender do Log4j2.
 * Observação: foi utilizada CopyOnWriteArraySet para simplicidade e segurança em concorrência.
 */
public final class LogStreamSessions {

    private static final Set<WebSocketSession> activeSessions = new CopyOnWriteArraySet<>();

    private LogStreamSessions() {}

    /**
     * Registra uma nova sessão WebSocket para receber mensagens de log.
     */
    public static void register(WebSocketSession session) {
        if (session != null) {
            activeSessions.add(session);
        }
    }

    /**
     * Remove uma sessão WebSocket (por fechamento de conexão ou erro).
     */
    public static void unregister(WebSocketSession session) {
        if (session != null) {
            activeSessions.remove(session);
        }
    }

    /**
     * Envia uma mensagem de texto para todas as sessões ativas.
     * Caso alguma sessão falhe no envio, ela é removida para manter a lista saudável.
     */
    public static void broadcast(String text) {
        for (WebSocketSession session : activeSessions) {
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(text));
                } catch (IOException ex) {
                    // Se falhar o envio, tentamos encerrar e remover a sessão problemática
                    try {
                        session.close();
                    } catch (IOException ignored) {
                        // Ignoramos falhas ao fechar
                    }
                    activeSessions.remove(session);
                }
            } else {
                activeSessions.remove(session);
            }
        }
    }
}