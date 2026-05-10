package com.practice.coursework.core;

import com.practice.coursework.io.TempIOManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

@Slf4j
@Service
public class BalancedMultiwaySorter implements ExternalSorter {
    private static final int CHUNK_SIZE = 100_000;

    @Override
    public void sort(Path inputFile, Path outputFile, Path tempDirectory) throws IOException {
        TempIOManager ioManager = new TempIOManager(tempDirectory);
        List<Path> sortedChunks = new ArrayList<>();

        try {
            log.info("Починаємо багатошляхове сортування. Розбиваємо файл на чанки...");
            sortedChunks = splitIntoSortedChunks(inputFile, ioManager);
            log.info("Злиття {} чанків у фінальний файл...", sortedChunks.size());
            mergeChunks(sortedChunks, outputFile);
            log.info("Сортування успішно завершено!");
        } finally {
            ioManager.cleanup();
        }
    }

    private List<Path> splitIntoSortedChunks(Path inputFile, TempIOManager ioManager) throws IOException {
        List<Path> chunks = new ArrayList<>();
        int[] buffer = new int[CHUNK_SIZE];
        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(inputFile)))) {
            long totalElements = Files.size(inputFile) / 4;
            long elementsRead = 0;
            while (elementsRead < totalElements) {
                int elementsToRead = (int) Math.min(CHUNK_SIZE, totalElements - elementsRead);

                for (int i = 0; i < elementsToRead; i++) {
                    buffer[i] = dis.readInt();
                }
                Arrays.sort(buffer, 0, elementsToRead);
                Path chunkPath = ioManager.writeChunk(buffer, elementsToRead);
                chunks.add(chunkPath);
                elementsRead += elementsToRead;
            }
        }
        return chunks;
    }

    private void mergeChunks(List<Path> chunkPaths, Path outputFile) throws IOException {
        List<DataInputStream> openStreams = new ArrayList<>();
        PriorityQueue<MergeNode> minHeap = new PriorityQueue<>();
        try (DataOutputStream outStream = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(outputFile)))) {
            for (Path chunkPath : chunkPaths) {
                long elementsInChunk = Files.size(chunkPath) / 4;
                if (elementsInChunk > 0) {
                    DataInputStream inStream = new DataInputStream(new BufferedInputStream(Files.newInputStream(chunkPath)));
                    openStreams.add(inStream);
                    int firstValue = inStream.readInt();
                    minHeap.add(new MergeNode(firstValue, inStream, elementsInChunk - 1));
                }
            }

            while (!minHeap.isEmpty()) {
                MergeNode minNode = minHeap.poll();
                outStream.writeInt(minNode.value);
                if (minNode.elementsRemaining > 0) {
                    minNode.value = minNode.stream.readInt();
                    minNode.elementsRemaining--;
                    minHeap.add(minNode);
                }
            }
        } finally {
            for (DataInputStream stream : openStreams) {
                try {
                    stream.close();
                } catch (IOException e) {
                    log.warn("Не вдалося закрити потік читання: {}", e.getMessage());
                }
            }
        }
    }

    private static class MergeNode implements Comparable<MergeNode> {
        int value;
        final DataInputStream stream;
        long elementsRemaining;

        public MergeNode(int value, DataInputStream stream, long elementsRemaining) {
            this.value = value;
            this.stream = stream;
            this.elementsRemaining = elementsRemaining;
        }

        @Override
        public int compareTo(MergeNode other) {
            return Integer.compare(this.value, other.value);
        }
    }
}