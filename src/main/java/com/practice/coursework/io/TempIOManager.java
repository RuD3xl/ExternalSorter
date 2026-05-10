package com.practice.coursework.io;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class TempIOManager {

    private final Path tempDirectory;
    private final List<Path> createdFiles;
    private int fileCounter = 0;

    public TempIOManager(Path tempDirectory) throws IOException {
        this.tempDirectory = tempDirectory;
        this.createdFiles = new ArrayList<>();
        if (!Files.exists(tempDirectory)) {
            Files.createDirectories(tempDirectory);
            log.info("Створено нову папку для тимчасових файлів: {}", tempDirectory.toAbsolutePath());
        }
    }

    public Path writeChunk(int[] data, int length) throws IOException {
        Path chunkPath = tempDirectory.resolve("chunk_" + (fileCounter++) + ".tmp");
        createdFiles.add(chunkPath);
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(chunkPath)))) {
            for (int i = 0; i < length; i++) {
                dos.writeInt(data[i]);
            }
        }

        log.debug("Збережено чанк: {}", chunkPath.getFileName());
        return chunkPath;
    }

    public void cleanup() {
        log.info("Починаємо очищення сміття");
        int deletedCount = 0;

        for (Path file : createdFiles) {
            try {
                if (Files.deleteIfExists(file)) {
                    deletedCount++;
                }
            } catch (IOException e) {
                log.warn("Не вдалося видалити файл: {}", file.getFileName());
            }
        }
        createdFiles.clear();
        log.info("Очищення завершено. Видалено файлів: {}", deletedCount);
    }
}