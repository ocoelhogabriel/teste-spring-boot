package com.ocoelho.controller;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private static final Logger logger = LoggerFactory.getLogger(LogController.class);

    @Value("${logging.file.path:./logs/application.log}")
    private String logFilePath;

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final ConcurrentHashMap<String, Long> filePositions = new ConcurrentHashMap<>();

    @GetMapping("/files")
    public ResponseEntity<List<LogFile>> getLogFiles() {
        logger.info("Recebida requisição para listar arquivos de log");

        try {
            Path logDir = getLogDirectory();

            if (!Files.exists(logDir)) {
                logger.warn("Diretório de logs não encontrado: {}", logDir);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            List<LogFile> files = Files.list(logDir)
                .filter(path -> path.toString().endsWith(".log"))
                .map(this::toLogFile)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            logger.debug("Retornando {} arquivos de log", files.size());
            return ResponseEntity.ok(files);
        } catch (IOException e) {
            logger.error("Erro ao listar arquivos de log", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private Path getLogDirectory() {
        // Obtém o caminho absoluto para o diretório de logs
        Path fullPath = Paths.get(logFilePath).toAbsolutePath().normalize();
        return fullPath.getParent(); // Retorna o diretório que contém o arquivo de log
    }

    private LogFile toLogFile(Path path) {
        try {
            return new LogFile(path.getFileName().toString(), Files.size(path), Files.getLastModifiedTime(path).toMillis());
        } catch (IOException e) {
            logger.error("Erro ao processar arquivo de log: {}", path, e);
            return null;
        }
    }

    @GetMapping("/file")
    public ResponseEntity<String> getLogFile(@RequestParam String name, @RequestParam(required = false) Integer lines) {
        logger.info("Recebida requisição para ler arquivo: {} (lines: {})", name, lines);

        try {
            Path filePath = getLogDirectory().resolve(name);

            if (!Files.exists(filePath)) {
                logger.warn("Arquivo de log não encontrado: {}", filePath);
                return ResponseEntity.notFound().build();
            }

            String content;
            if (lines != null && lines > 0) {
                logger.debug("Lendo últimas {} linhas do arquivo {}", lines, name);
                try (Stream<String> lineStream = Files.lines(filePath)) {
                    content = lineStream.skip(Math.max(0, Files.lines(filePath).count() - lines)).collect(Collectors.joining("\n"));
                }
            } else {
                logger.debug("Lendo arquivo completo {}", name);
                content = Files.readString(filePath);
            }

            logger.debug("Arquivo {} lido com sucesso. Tamanho: {} chars", name, content.length());
            return ResponseEntity.ok(content);
        } catch (IOException e) {
            logger.error("Erro ao ler arquivo de log: {}", name, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamLogs(@RequestParam(required = false) String file, @RequestParam(required = false) String level) {

        String logFile = (file != null) ? file : Paths.get(logFilePath).getFileName().toString();
        logger.info("Iniciando stream de logs para o arquivo: {} (level: {})", logFile, level);

        SseEmitter emitter = new SseEmitter(0L); // Timeout infinito

        executor.execute(() -> {
            try {
                Path path = getLogDirectory().resolve(logFile);
                String fileKey = path.toString();

                if (!Files.exists(path)) {
                    emitter.send("Log file not found: " + path);
                    emitter.complete();
                    return;
                }

                long lastPosition = filePositions.getOrDefault(fileKey, 0L);
                monitorLogFile(emitter, path, fileKey, lastPosition, level);
            } catch (Exception e) {
                logger.error("Erro no stream de logs", e);
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void monitorLogFile(SseEmitter emitter, Path path, String fileKey, long lastPosition, String level) throws Exception {

        boolean shouldContinue = true;

        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r");
                WatchService watchService = FileSystems.getDefault().newWatchService()) {

            Path dir = path.getParent();
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);

            readNewLines(file, emitter, fileKey, lastPosition, level);

            while (shouldContinue) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (ClosedWatchServiceException e) {
                    logger.debug("WatchService fechado para {}", fileKey);
                    shouldContinue = false;
                    continue;
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.context().toString().equals(path.getFileName().toString())) {
                        logger.trace("Modificação detectada no arquivo {}", fileKey);
                        readNewLines(file, emitter, fileKey, filePositions.get(fileKey), level);
                    }
                }

                shouldContinue = key.reset();
            }
        }
    }

    private void readNewLines(RandomAccessFile file, SseEmitter emitter, String fileKey, long lastPosition, String level) throws IOException {

        long fileSize = file.length();

        if (fileSize < lastPosition) {
            logger.info("Rotação de log detectada para {}. Reiniciando posição.", fileKey);
            lastPosition = 0;
        }

        if (fileSize > lastPosition) {
            file.seek(lastPosition);
            logger.debug("Lendo novas linhas do arquivo {} (posição {} a {})", fileKey, lastPosition, fileSize);

            String line;
            int lineCount = 0;
            while ((line = file.readLine()) != null) {
                if (shouldSendLine(line, level)) {
                    try {
                        emitter.send(SseEmitter.event().data(line).id(String.valueOf(file.getFilePointer())));
                        lineCount++;
                    } catch (IOException e) {
                        logger.error("Falha ao enviar linha via SSE", e);
                        throw e;
                    }
                }
            }

            long newPosition = file.getFilePointer();
            filePositions.put(fileKey, newPosition);
            logger.trace("{} novas linhas enviadas. Nova posição: {}", lineCount, newPosition);
        }
    }

    private boolean shouldSendLine(String line, String level) {
        if (level == null || level.isEmpty()) {
            return true;
        }
        return line.contains("[" + level + "]");
    }

    public static class LogFile {
        private final String name;
        private final long size;
        private final long lastModified;

        public LogFile(String name, long size, long lastModified) {
            this.name = name;
            this.size = size;
            this.lastModified = lastModified;
        }

        public String getName() {
            return name;
        }

        public long getSize() {
            return size;
        }

        public long getLastModified() {
            return lastModified;
        }
    }
}