package com.ocoelho.service;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

import org.springframework.stereotype.Service;

import com.ocoelho.properties.AppLoggingProperties;

@Service
public class LogFileService {

    private final Path logsDir;

    public LogFileService(AppLoggingProperties properties) {
        this.logsDir = properties.getDir();
    }

    /**
     * Lista arquivos de log (incluindo .log e .log.gz) com informações básicas.
     */
    public List<LogFileInfoDTO> listLogFiles() throws IOException {
        if (!Files.exists(logsDir)) {
            return List.of();
        }
        List<LogFileInfoDTO> result = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(logsDir)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    String fileName = path.getFileName().toString();
                    if (fileName.endsWith(".log") || fileName.endsWith(".gz")) {
                        LogFileInfoDTO info = new LogFileInfoDTO();
                        info.setFileName(fileName);
                        info.setSizeBytes(Files.size(path));
                        info.setLastModifiedMillis(Files.getLastModifiedTime(path).toMillis());
                        info.setCompressed(fileName.endsWith(".gz"));
                        result.add(info);
                    }
                }
            }
        }
        // Ordena por data de modificação (mais recentes primeiro)
        result.sort(Comparator.comparingLong(LogFileInfoDTO::getLastModifiedMillis).reversed());
        return result;
    }

    /**
     * Abre um InputStream para um arquivo dentro do diretório de logs de forma segura. Faz verificação para evitar path traversal.
     */
    public InputStream openLogFileStreamSecure(String requestedFileName) throws IOException {
        validateFileName(requestedFileName);
        Path filePath = logsDir.resolve(requestedFileName).normalize();
        if (!filePath.startsWith(logsDir.normalize())) {
            throw new SecurityException("Tentativa de acesso a caminho fora do diretório de logs.");
        }
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new FileNotFoundException("Arquivo de log nao encontrado.");
        }

        InputStream baseStream = Files.newInputStream(filePath, StandardOpenOption.READ);
        if (requestedFileName.endsWith(".gz")) {
            return new GZIPInputStream(baseStream);
        }
        return baseStream;
    }

    /**
     * Lê as últimas 'limit' linhas do arquivo (ou arquivo .gz) aplicando um filtro regex opcional. A leitura é feita mantendo um buffer limitado em memória (fila circular) para eficiência.
     */
    public List<String> tailWithFilter(String requestedFileName, int limit, String regexFilter) throws IOException {
        validateFileName(requestedFileName);
        if (limit <= 0) {
            limit = 200; // valor padrão seguro
        }
        Pattern pattern = (regexFilter == null || regexFilter.isBlank()) ? null : Pattern.compile(regexFilter);

        try (InputStream in = openLogFileStreamSecure(requestedFileName);
                InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
                BufferedReader bufferedReader = new BufferedReader(reader)) {

            ArrayDeque<String> lastLines = new ArrayDeque<>(limit);
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (pattern == null || pattern.matcher(line).find()) {
                    if (lastLines.size() == limit) {
                        lastLines.removeFirst();
                    }
                    lastLines.addLast(line);
                }
            }
            return new ArrayList<>(lastLines);
        }
    }

    /**
     * Procura por linhas que correspondam a uma expressão regular em todo o arquivo
     * de log. A busca é feita de forma sequencial e para no momento em que o
     * número máximo de resultados (limit) é atingido.
     */
    public List<String> searchLines(String requestedFileName, String regexFilter, int limit) throws IOException {
        validateFileName(requestedFileName);
        if (regexFilter == null || regexFilter.isBlank()) {
            return List.of();
        }
        Pattern pattern = Pattern.compile(regexFilter);
        if (limit <= 0) {
            limit = Integer.MAX_VALUE;
        }

        try (InputStream in = openLogFileStreamSecure(requestedFileName);
                InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8);
                BufferedReader bufferedReader = new BufferedReader(reader)) {

            List<String> matches = new ArrayList<>();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if (pattern.matcher(line).find()) {
                    matches.add(line);
                    if (matches.size() >= limit) {
                        break;
                    }
                }
            }
            return matches;
        }
    }

    private void validateFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Nome de arquivo invalido.");
        }
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new SecurityException("Nome de arquivo suspeito. Caminhos relativos nao sao permitidos.");
        }
    }
}
