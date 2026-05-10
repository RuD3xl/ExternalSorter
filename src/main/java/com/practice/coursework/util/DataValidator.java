package com.practice.coursework.util;

import javafx.fxml.FXML;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class DataValidator {

    public static boolean isFileSorted(Path filePath) throws IOException {
        long totalElements = Files.size(filePath) / 4;

        if (totalElements <= 1) return true;

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(filePath)))) {
            int previous = dis.readInt();

            for (long i = 1; i < totalElements; i++) {
                int current = dis.readInt();

                if (current < previous) {
                    log.error("Помилка сортування. Знайдено порушення: {} -> {} (на позиції {})", previous, current, i);
                    return false;
                }
                previous = current;
            }
        }
        return true;
    }

}