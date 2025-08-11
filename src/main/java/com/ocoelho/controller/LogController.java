package com.ocoelho.controller;

import com.ocoelho.dto.LogFileDto;
import com.ocoelho.service.LogService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping
    public Page<LogFileDto> list(@RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "10") int size) throws IOException {
        return logService.list(page, size);
    }

    @GetMapping("/{fileName:.+}")
    public ResponseEntity<Resource> download(@PathVariable String fileName) throws IOException {
        Path file = logService.resolve(fileName);
        if (Files.notExists(file)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new InputStreamResource(Files.newInputStream(file));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
