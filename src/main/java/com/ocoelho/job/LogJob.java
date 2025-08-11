package com.ocoelho.job;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

@Component
public class LogJob {

    private static final Logger logger = LoggerFactory.getLogger(LogJob.class);

    @PreDestroy
    public void onDestroy() {
        logger.info("LogJob está sendo destruído. Limpeza de recursos, se necessário.");
    }

    @Scheduled(fixedRate = 5000) // executa periodicamente; considerar fixedDelay se quiser medir a partir do término
    public void execute() {
        long startMillis = System.currentTimeMillis();
        logger.info("LogJob iniciado em {}", Instant.ofEpochMilli(startMillis));

        try {
            // Simula processamento em 4 passos, com pausas e avisos eventuais
            for (int passo = 1; passo <= 4; passo++) {
                logger.debug("Processando passo {} ...", passo);
                Thread.sleep(1000L); // pausa de 1s para simular trabalho

                // Cerca de 10% de chance de um WARN intermitente
                if (ThreadLocalRandom.current().nextInt(10) == 0) {
                    logger.warn("Condicao intermitente detectada no passo {}", passo);
                }
            }

            // Cerca de 7% de chance de simular uma falha para gerar ERROR
            if (ThreadLocalRandom.current().nextInt(15) == 0) {
                throw new IllegalStateException("Falha simulada para teste de ERROR.");
            }

            long elapsed = System.currentTimeMillis() - startMillis;
            logger.info("LogJob finalizado com sucesso em {} ms", elapsed);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            logger.error("LogJob interrompido", ie);

        } catch (Exception ex) {
            logger.error("LogJob falhou", ex);
        }
    }
}
