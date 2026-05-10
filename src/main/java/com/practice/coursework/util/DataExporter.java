package com.practice.coursework.util;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Slf4j
public class DataExporter {

    public static void exportBinaryToText(Path binFile, Path txtFile, long startIndex, long count) throws IOException {
        long totalElements = Files.size(binFile) / 4;

        if (startIndex >= totalElements) {
            log.error("Помилка: У файлі всього {} чисел. Не можна почати з {}", totalElements, startIndex);
            return;
        }

        long elementsToExport = (count > 0 && (startIndex + count) < totalElements)
                ? count
                : (totalElements - startIndex);

        log.info("Експорт {} чисел (починаючи з позиції {})...", elementsToExport, startIndex);

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(binFile)));
             PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(txtFile.toFile())))) {

            for (long i = 0; i < startIndex; i++) {
                dis.readInt();
            }

            for (long i = 0; i < elementsToExport; i++) {
                writer.println(dis.readInt());
            }
        }

        log.info("Експорт завершено! Результат збережено у: {}", txtFile.getFileName());
    }

    public static void exportBinaryToBinary(Path inputPath, Path outputPath, long start, long count) throws IOException {
        long byteOffset = start * 4L;
        long bytesToCopy = count * 4L;

        try (FileChannel sourceChannel = FileChannel.open(inputPath, StandardOpenOption.READ);
             FileChannel destChannel = FileChannel.open(outputPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

            long fileSize = sourceChannel.size();

            if (byteOffset >= fileSize) {
                throw new IllegalArgumentException("Початкова позиція виходить за межі файлу! У файлі лише " + (fileSize / 4) + " елементів.");
            }
            if (byteOffset + bytesToCopy > fileSize) {
                bytesToCopy = fileSize - byteOffset;
            }

            sourceChannel.transferTo(byteOffset, bytesToCopy, destChannel);
        }
    }
}