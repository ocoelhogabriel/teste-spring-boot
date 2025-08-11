package com.ocoelho.service;

import jakarta.annotation.PostConstruct;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class LogTailService {

    private final SimpMessagingTemplate messagingTemplate;
    private final Path logFile = Paths.get("log/application.log");
    private long filePointer = 0L;

    public LogTailService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @PostConstruct
    public void init() throws IOException {
        if (Files.notExists(logFile)) {
            Files.createDirectories(logFile.getParent());
            Files.createFile(logFile);
        }
        filePointer = Files.size(logFile);
    }

    @Scheduled(fixedDelay = 1000)
    public void tailLogFile() throws IOException {
        long fileSize = Files.size(logFile);
        if (fileSize > filePointer) {
            try (RandomAccessFile file = new RandomAccessFile(logFile.toFile(), "r")) {
                file.seek(filePointer);
                String line;
                while ((line = file.readLine()) != null) {
                    messagingTemplate.convertAndSend("/topic/logs", line);
                }
                filePointer = file.getFilePointer();
            }
        }
    }
}
