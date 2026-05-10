package com.practice.coursework.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
public class PolyphaseSorter implements ExternalSorter {
    private static final int CHUNK_SIZE = 100_000;

    @Override
    public void sort(Path inputFile, Path outputFile, Path tempDirectory) throws IOException {
        Path[] files = new Path[3];
        files[0] = tempDirectory.resolve("poly_0.tmp");
        files[1] = tempDirectory.resolve("poly_1.tmp");
        files[2] = tempDirectory.resolve("poly_2.tmp");

        try {
            log.info("Фаза 1: Генерація чанків та розподіл за числами Фібоначчі");
            int[] runCounts = generateAndDistributeFibonacci(inputFile, files);
            log.info("Розподілено серій: Файл 0 = {}, Файл 1 = {}", runCounts[0], runCounts[1]);

            if (runCounts[0] == 0 && runCounts[1] == 0) return;

            if (runCounts[0] <= 1 && runCounts[1] == 0) {
                Files.copy(files[0], outputFile, StandardCopyOption.REPLACE_EXISTING);
                return;
            }

            log.info("Фаза 2: Багатофазне злиття з ротацією файлів...");
            int finalOutputIndex = mergePolyphase(files, runCounts);

            Files.copy(files[finalOutputIndex], outputFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Сортування завершено! Результат збережено.");

        } finally {
            for (Path p : files) {
                Files.deleteIfExists(p);
            }
        }
    }

    private int[] generateAndDistributeFibonacci(Path inputFile, Path[] files) throws IOException {
        List<Path> tempChunks = new ArrayList<>();
        int[] buffer = new int[CHUNK_SIZE];

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(inputFile)))) {
            long totalElements = Files.size(inputFile) / 4;
            long read = 0;
            int chunkIndex = 0;

            while (read < totalElements) {
                int toRead = (int) Math.min(CHUNK_SIZE, totalElements - read);
                for (int i = 0; i < toRead; i++) buffer[i] = in.readInt();
                Arrays.sort(buffer, 0, toRead);

                Path chunkPath = files[0].getParent().resolve("temp_chunk_" + chunkIndex++ + ".tmp");
                try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(chunkPath)))) {
                    for (int i = 0; i < toRead; i++) out.writeInt(buffer[i]);
                }
                tempChunks.add(chunkPath);
                read += toRead;
            }
        }

        int totalRuns = tempChunks.size();

        int fib1 = 1, fib2 = 1;
        while (fib1 + fib2 < totalRuns) {
            int nextFib = fib1 + fib2;
            fib1 = fib2;
            fib2 = nextFib;
        }
        int targetRuns0 = fib2;
        int targetRuns1 = fib1;
        int runsWritten0 = 0;

        try (DataOutputStream out0 = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(files[0])));
             DataOutputStream out1 = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(files[1])))) {
            for (Path chunk : tempChunks) {
                DataOutputStream currentOut = (runsWritten0 < targetRuns0) ? out0 : out1;
                try (DataInputStream chunkIn = new DataInputStream(new BufferedInputStream(Files.newInputStream(chunk)))) {
                    long elements = Files.size(chunk) / 4;
                    for (long e = 0; e < elements; e++) {
                        currentOut.writeInt(chunkIn.readInt());
                    }
                }
                if (currentOut == out0) runsWritten0++;
                Files.deleteIfExists(chunk);
            }
        }
        return new int[]{targetRuns0, targetRuns1, 0};
    }

    private int mergePolyphase(Path[] files, int[] runCounts) throws IOException {
        int in1 = 0, in2 = 1, out = 2;

        DataInputStream[] ins = new DataInputStream[3];
        DataOutputStream[] outs = new DataOutputStream[3];

        ins[in1] = new DataInputStream(new BufferedInputStream(Files.newInputStream(files[in1])));
        ins[in2] = new DataInputStream(new BufferedInputStream(Files.newInputStream(files[in2])));
        outs[out] = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(files[out])));
        Integer[] heads = new Integer[3];
        heads[in1] = readNext(ins[in1]);
        heads[in2] = readNext(ins[in2]);

        while (runCounts[in1] > 0 && runCounts[in2] > 0) {
            int runsToMerge = Math.min(runCounts[in1], runCounts[in2]);
            log.debug("  Зливаємо {} серій з Файлу {} та Файлу {} -> у Файл {}", runsToMerge, in1, in2, out);

            for (int i = 0; i < runsToMerge; i++) {
                mergeSingleRun(ins, outs[out], heads, in1, in2);
            }
            runCounts[out] += runsToMerge;
            runCounts[in1] -= runsToMerge;
            runCounts[in2] -= runsToMerge;

            if (runCounts[in1] == 0) {
                ins[in1].close();
                outs[out].close();

                ins[out] = new DataInputStream(new BufferedInputStream(Files.newInputStream(files[out])));
                heads[out] = readNext(ins[out]);

                outs[in1] = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(files[in1])));

                int temp = out; out = in1; in1 = temp;
            } else {
                ins[in2].close();
                outs[out].close();

                ins[out] = new DataInputStream(new BufferedInputStream(Files.newInputStream(files[out])));
                heads[out] = readNext(ins[out]);

                outs[in2] = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(files[in2])));

                int temp = out; out = in2; in2 = temp;
            }
        }

        for (int i = 0; i < 3; i++) {
            if (ins[i] != null) ins[i].close();
            if (outs[i] != null) outs[i].close();
        }

        return runCounts[in1] > 0 ? in1 : in2;
    }

    private void mergeSingleRun(DataInputStream[] ins, DataOutputStream outStream, Integer[] heads, int in1, int in2) throws IOException {
        boolean run1Active = heads[in1] != null;
        boolean run2Active = heads[in2] != null;

        while (run1Active || run2Active) {
            boolean takeFrom1;

            if (!run1Active) takeFrom1 = false;
            else if (!run2Active) takeFrom1 = true;
            else takeFrom1 = heads[in1] <= heads[in2];

            if (takeFrom1) {
                outStream.writeInt(heads[in1]);
                int prev = heads[in1];
                heads[in1] = readNext(ins[in1]);
                if (heads[in1] == null || heads[in1] < prev) run1Active = false;
            } else {
                outStream.writeInt(heads[in2]);
                int prev = heads[in2];
                heads[in2] = readNext(ins[in2]);
                if (heads[in2] == null || heads[in2] < prev) run2Active = false;
            }
        }
    }

    private Integer readNext(DataInputStream in) {
        try {
            return in.readInt();
        } catch (EOFException e) {
            return null;
        } catch (IOException e) {
            throw new RuntimeException("Помилка диска при читанні", e);
        }
    }
}