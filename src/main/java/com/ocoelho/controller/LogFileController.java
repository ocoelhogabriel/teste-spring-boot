package com.ocoelho.controller;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ocoelho.service.LogFileInfoDTO;
import com.ocoelho.service.LogFileService;

/**
 * API REST para manipulação de arquivos de log. Permite:
 * <ul>
 *   <li>Listar arquivos disponíveis;</li>
 *   <li>Buscar informações em um arquivo;</li>
 *   <li>Visualizar trecho recente (tail) com filtro opcional;</li>
 *   <li>Efetuar download do arquivo completo.</li>
 * </ul>
 */
@RestController
@RequestMapping("/logs")
public class LogFileController {

    private final LogFileService logFileService;

    public LogFileController(LogFileService logFileService) {
        this.logFileService = logFileService;
    }

    /**
     * Lista arquivos de log disponíveis no diretório configurado.
     */
    @GetMapping("/files")
    public List<LogFileInfoDTO> listFiles() throws Exception {
        return logFileService.listLogFiles();
    }

    /**
     * Visualiza as últimas 'limit' linhas de um arquivo, aplicando filtro por expressão regular (grep) caso informado. Exemplo: GET /logs/view?file=application.log&limit=200&grep=ERROR
     */
    @GetMapping("/view")
    public List<String> viewTail(@RequestParam("file") String fileName, @RequestParam(required = false, defaultValue = "200") int limit,
            @RequestParam(required = false) String grepRegex) throws Exception {
        return logFileService.tailWithFilter(fileName, limit, grepRegex);
    }

    /**
     * Busca por linhas que contenham a expressão regular informada em todo o arquivo de log.
     * Exemplo: GET /logs/search?file=application.log&grep=ERROR&limit=500
     */
    @GetMapping("/search")
    public List<String> search(@RequestParam("file") String fileName,
                               @RequestParam("grep") String grepRegex,
                               @RequestParam(required = false, defaultValue = "100") int limit) throws Exception {
        return logFileService.searchLines(fileName, grepRegex, limit);
    }

    /**
     * Download do arquivo integral (seja .log ou .log.gz). Exemplo: GET /logs/download/application-2025-08-10-1.log.gz
     */
    @GetMapping("/download/{fileName}")
    public ResponseEntity<InputStreamResource> download(@PathVariable String fileName) throws Exception {
        InputStream inputStream = logFileService.openLogFileStreamSecure(fileName);

        // Define o content-type de forma simples: texto para .log, octet-stream para .gz
        MediaType mediaType = fileName.endsWith(".gz") ? MediaType.APPLICATION_OCTET_STREAM : MediaType.TEXT_PLAIN;

        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        ContentDisposition disposition = ContentDisposition.attachment().filename(encoded).build();

        return ResponseEntity.ok()
            .contentType(mediaType)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
            .body(new InputStreamResource(inputStream));
    }
}
