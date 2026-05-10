package com.practice.coursework.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Slf4j
@Service
public class NaturalMergeSorter implements ExternalSorter {

    @Override
    public void sort(Path inputFile, Path outputFile, Path tempDirectory) throws IOException {
        Path tempA = tempDirectory.resolve("natural_A.tmp");
        Path tempB = tempDirectory.resolve("natural_B.tmp");

        try {
            log.info("Починаємо природне злиття. Розподіл вихідного файлу...");
            int runs = distributeNaturalRuns(inputFile, tempA, tempB);
            if (runs <= 1) {
                log.info("Файл вже відсортований!");
                Files.copy(inputFile, outputFile, StandardCopyOption.REPLACE_EXISTING);
                return;
            }

            int passCount = 0;
            boolean mergeActive = true;
            while (mergeActive) {
                passCount++;
                int mergedRuns = mergeNaturalRuns(tempA, tempB, outputFile);
                log.info("Прохід злиття #{}, залишилось серій: {}", passCount,mergedRuns);

                if (mergedRuns <= 1) {
                    log.info("Сортування завершено за {} проходів!", passCount);
                    mergeActive = false;
                }

                distributeNaturalRuns(outputFile, tempA, tempB);
            }

        } finally {
            Files.deleteIfExists(tempA);
            Files.deleteIfExists(tempB);
        }
    }

    private int distributeNaturalRuns(Path source, Path fileA, Path fileB) throws IOException {
        int runCount = 0;

        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(source)));
             DataOutputStream outA = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(fileA)));
             DataOutputStream outB = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(fileB)))) {

            long totalElements = Files.size(source) / 4;
            if (totalElements == 0) return 0;

            int prev = in.readInt();
            long read = 1;
            boolean writeToA = true;
            DataOutputStream currentOut = outA;

            currentOut.writeInt(prev);
            runCount = 1;

            while (read < totalElements) {
                int curr = in.readInt();
                read++;

                if (curr < prev) {
                    writeToA = !writeToA;
                    currentOut = writeToA ? outA : outB;
                    runCount++;
                }
                currentOut.writeInt(curr);
                prev = curr;
            }
        }
        return runCount;
    }

    private int mergeNaturalRuns(Path fileA, Path fileB, Path targetFile) throws IOException {
        int mergedRunsCount = 0;
        long totalA = Files.size(fileA) / 4;
        long totalB = Files.size(fileB) / 4;

        try (DataInputStream inA = new DataInputStream(new BufferedInputStream(Files.newInputStream(fileA)));
             DataInputStream inB = new DataInputStream(new BufferedInputStream(Files.newInputStream(fileB)));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(targetFile)))) {
            long readA = 0, readB = 0;
            Integer valA = (readA < totalA) ? inA.readInt() : null;
            if (valA != null) readA++;

            Integer valB = (readB < totalB) ? inB.readInt() : null;
            if (valB != null) readB++;

            while (valA != null || valB != null) {
                int i = mergedRunsCount++;
                boolean runAActive = (valA != null);
                boolean runBActive = (valB != null);
                while (runAActive || runBActive) {
                    boolean takeFromA;
                    if (!runAActive) {
                        takeFromA = false;
                    } else if (!runBActive) {
                        takeFromA = true;
                    } else {
                        takeFromA = valA <= valB;
                    }

                    if (takeFromA) {
                        out.writeInt(valA);
                        int prevA = valA;

                        if (readA < totalA) {
                            valA = inA.readInt();
                            readA++;
                            if (valA < prevA) runAActive = false;
                        } else {
                            valA = null;
                            runAActive = false;
                        }
                    } else {
                        out.writeInt(valB);
                        int prevB = valB;
                        if (readB < totalB) {
                            valB = inB.readInt();
                            readB++;
                            if (valB < prevB) runBActive = false;
                        } else {
                            valB = null;
                            runBActive = false;
                        }
                    }
                }
            }
        }
        return mergedRunsCount;
    }
}