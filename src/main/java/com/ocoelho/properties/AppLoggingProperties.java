package com.ocoelho.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Carrega propriedades customizadas para o módulo de observabilidade. Prefixo: app.logging.*
 */
@Configuration
@ConfigurationProperties(prefix = "app.logging")
public class AppLoggingProperties {

    /**
     * Diretório base onde os arquivos de log são gravados e buscados. Valor padrão: ./logs
     */
    private Path dir = Paths.get("./logs");

    /**
     * Lista de origens permitidas para o WebSocket. Útil para controlar CORS do WebSocket em navegadores e ferramentas.
     */
    private List<String> allowedOrigins = List.of("*");

    public Path getDir() {
        return dir;
    }

    public void setDir(Path dir) {
        this.dir = dir;
    }

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
}
