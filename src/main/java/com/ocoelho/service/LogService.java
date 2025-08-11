package com.ocoelho.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.ocoelho.dto.LogFileDto;

@Service
public class LogService {

    private final Path logDir = Paths.get("log");

    public Page<LogFileDto> list(int page, int size) throws IOException {
        if (Files.notExists(logDir)) {
            return Page.empty(PageRequest.of(page, size));
        }

        List<LogFileDto> files = Files.list(logDir).filter(Files::isRegularFile).map(p -> {
            try {
                return new LogFileDto(p.getFileName().toString(), Files.size(p), Files.getLastModifiedTime(p).toInstant());
            } catch (IOException e) {
                return null;
            }
        }).filter(f -> f != null).sorted(Comparator.comparing(LogFileDto::lastModified).reversed()).collect(Collectors.toList());

        int from = Math.min(page * size, files.size());
        int to = Math.min(from + size, files.size());
        List<LogFileDto> pageContent = files.subList(from, to);
        return new PageImpl<>(pageContent, PageRequest.of(page, size), files.size());
    }

    public Path resolve(String filename) {
        Path file = logDir.resolve(filename).normalize();
        if (!file.startsWith(logDir)) {
            throw new IllegalArgumentException("Invalid file path");
        }
        return file;
    }
}
