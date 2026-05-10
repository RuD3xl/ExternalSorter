package com.practice.coursework.util;

import lombok.extern.slf4j.Slf4j;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class DataImporter {
    public static long importTextToBinary(Path txtFile, Path binFile) throws IOException {
        log.info("Початок конвертації текстового файлу в бінарний формат...");
        long elementsImported = 0;

        try (BufferedReader reader = Files.newBufferedReader(txtFile);
             DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(binFile)))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                try {
                    dos.writeInt(Integer.parseInt(line));
                    elementsImported++;
                } catch (NumberFormatException e) {
                    log.warn("Пропущено некоректний рядок (не число): {}", line);
                }
            }
        }
        log.info("Імпорт успішно завершено! Оброблено чисел: {}", elementsImported);
        return elementsImported;
    }
}