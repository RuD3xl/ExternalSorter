package com.practice.coursework.core;

import java.io.IOException;
import java.nio.file.Path;

public interface ExternalSorter {
    void sort(Path inputFile, Path outputFile, Path tempDirectory) throws IOException;
}