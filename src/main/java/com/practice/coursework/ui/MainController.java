package com.practice.coursework.ui;

import com.practice.coursework.core.*;
import com.practice.coursework.util.*;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

@Component
public class MainController {

    @Autowired
    private BalancedMultiwaySorter balancedSorter;
    @Autowired
    private NaturalMergeSorter naturalSorter;
    @Autowired
    private PolyphaseSorter polyphaseSorter;
    @FXML private RadioButton rbSelect, rbGenerate;
    @FXML private HBox paneSelect, paneGenerate;
    @FXML private TextField txtFilePath, txtGenerateCount;
    @FXML private ComboBox<String> comboAlgorithm;
    @FXML private Label lblInfo;
    @FXML private Button btnStart;
    @FXML private ProgressBar progressBar;
    @FXML private TextArea txtLog;
    @FXML private TextField txtExportStart, txtExportCount;
    private Tooltip infoTooltip;
    private File selectedInputFile;
    private Path finalBinaryOutput;

    private final StringBuilder detailedLogs = new StringBuilder();

    @FXML
    public void initialize() {
        ToggleGroup sourceGroup = new ToggleGroup();
        rbSelect.setToggleGroup(sourceGroup);
        rbGenerate.setToggleGroup(sourceGroup);

        infoTooltip = new Tooltip();
        infoTooltip.setWrapText(true);
        infoTooltip.setPrefWidth(300);
        infoTooltip.setStyle("-fx-font-size: 13px; -fx-background-color: white; -fx-text-fill: black; -fx-border-color: gray;");
        infoTooltip.setShowDelay(javafx.util.Duration.millis(100));
        infoTooltip.setShowDuration(javafx.util.Duration.seconds(15));
        Tooltip.install(lblInfo, infoTooltip);

        comboAlgorithm.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) updateTooltip(newVal);
        });

        comboAlgorithm.getSelectionModel().selectFirst();

        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(originalOut, true, StandardCharsets.UTF_8) {
            @Override
            public void write(byte[] buf, int off, int len) {
                super.write(buf, off, len);
                detailedLogs.append(new String(buf, off, len, StandardCharsets.UTF_8));
            }
        });

        logToScreen("Програму успішно запущено. Оберіть джерело даних");
    }

    @FXML
    public void handleSourceToggle(ActionEvent event) {
        if (rbSelect.isSelected()) {
            paneSelect.setVisible(true);
            paneGenerate.setVisible(false);
        } else {
            paneSelect.setVisible(false);
            paneGenerate.setVisible(true);
        }
    }

    private void updateTooltip(String algorithm) {
        String text = switch (algorithm) {
            case "Збалансоване багатошляхове злиття" ->
                    "Розбиває файл на частини, сортує їх у пам'яті, а потім одночасно зливає всі відрізки в один фінальний файл за допомогою структури даних 'Мін-Купа' (PriorityQueue).";
            case "Природне злиття" ->
                    "Шукає вже відсортовані послідовності (серії) у вихідному файлі і зливає їх між двома допоміжними файлами (пінг-понг), доки не залишиться лише одна серія.";
            case "Багатофазне злиття" ->
                    "Використовує числа Фібоначчі для ідеального розподілу серій. Динамічно змінює ролі допоміжних файлів для зменшення кількості операцій на жорсткому диску.";
            default -> "Опис відсутній";
        };

        if (infoTooltip != null) {
            infoTooltip.setText(text);
        }
    }

    @FXML
    public void handleSelectFile(ActionEvent event) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Виберіть файл для сортування");

        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Допустимі файли (*.txt, *.bin)", "*.txt", "*.bin"),
                new FileChooser.ExtensionFilter("Текстові файли (*.txt)", "*.txt"),
                new FileChooser.ExtensionFilter("Бінарні файли (*.bin)", "*.bin")
        );

        Stage stage = (Stage) txtFilePath.getScene().getWindow();
        selectedInputFile = fileChooser.showOpenDialog(stage);

        if (selectedInputFile != null) {
            txtFilePath.setText(selectedInputFile.getAbsolutePath());
        }

    }

    @FXML
    public void handleShowLogs(ActionEvent event) {
        String rawLogs = detailedLogs.toString();
        String cleanLogs = rawLogs.replaceAll("(?m)^\\d{4}-\\d{2}-\\d{2}T.*?\\s:\\s", "");

        TextArea fullLogArea = new TextArea(cleanLogs);
        fullLogArea.setEditable(false);
        fullLogArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 13px;");

        Stage logStage = new Stage();
        logStage.setTitle("Детальний журнал виконання");
        logStage.setScene(new Scene(fullLogArea, 700, 500));
        logStage.show();
    }

    @FXML
    public void handleStartSort(ActionEvent event) {
        Path inputPath;
        final Path workDir = Paths.get(System.getProperty("user.dir"), "temp_sort_data");

        try {
            if (!Files.exists(workDir)) {
                Files.createDirectories(workDir);
            }
        } catch (IOException e) {
            logToScreen("Критична помилка: Не вдалося створити папку для тимчасових даних.");
            return;
        }
        if (rbSelect.isSelected()) {
            if (selectedInputFile == null) {
                logToScreen("Помилка: Спочатку виберіть файл!");
                return;
            }

            inputPath = selectedInputFile.toPath();
            if (!isSafeFile(selectedInputFile)) {
                return;
            }
        } else {
            try {
                int count = Integer.parseInt(txtGenerateCount.getText());
                if (count <= 1) {
                    logToScreen("Помилка генерації: Число повинно бути більше 1");
                    return;
                }
                if (!Files.exists(workDir)) Files.createDirectories(workDir);
                inputPath = workDir.resolve("generated_input.bin");
                logToScreen("Генерація " + count + " випадкових чисел..");
                generateRandomBinaryFile(inputPath, count);
                logToScreen("Файл згенеровано: " + inputPath.getFileName());
            } catch (Exception e) {
                logToScreen("Помилка генерації: Перевірте правильність введеного числа.");
                return;
            }
        }

        String algorithmName = comboAlgorithm.getValue();
        int algorithmIndex = comboAlgorithm.getSelectionModel().getSelectedIndex();

        logToScreen("===================================");
        logToScreen("Запуск сортування. Метод: " + algorithmName);

        btnStart.setDisable(true);
        progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        final Path finalWorkDir = workDir;
        final Path finalInputPath = inputPath;

        Task<Void> sortTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                Path tempDir = finalWorkDir.resolve("temp_sort");
                if (!Files.exists(tempDir)) Files.createDirectories(tempDir);

                Path workingBinFile = finalInputPath;

                if (finalInputPath.toString().endsWith(".txt")) {
                    Platform.runLater(() -> logToScreen("Сжимаємо .txt у бінарний формат..."));
                    workingBinFile = finalWorkDir.resolve("working_input.bin");
                    DataImporter.importTextToBinary(finalInputPath, workingBinFile);
                }

                finalBinaryOutput = finalWorkDir.resolve("sorted_output.bin");

                ExternalSorter sorter = switch (algorithmIndex) {
                    case 1 -> naturalSorter;
                    case 2 -> polyphaseSorter;
                    default -> balancedSorter;
                };

                Platform.runLater(() -> logToScreen("Алгоритм працює. Обробка файлу на диску..."));
                sorter.sort(workingBinFile, finalBinaryOutput, tempDir);

                Platform.runLater(() -> logToScreen("Сортування завершено. Перевірка цілісності..."));
                if (DataValidator.isFileSorted(finalBinaryOutput)) {
                    Platform.runLater(() -> logToScreen("Успіх! Файл ідеально відсортовано."));
                } else {
                    Platform.runLater(() -> logToScreen("Помилка: Знайдено порушення!"));
                }
                return null;
            }
        };

        sortTask.setOnSucceeded(e -> {
            progressBar.setProgress(1.0);
            btnStart.setDisable(false);
            logToScreen("Готово! Можете експортувати результат або подивитися Детальні логи.");
        });

        sortTask.setOnFailed(e -> {
            progressBar.setProgress(0);
            btnStart.setDisable(false);
            Throwable error = sortTask.getException();
            logToScreen("Помилка (" + error.getClass().getSimpleName() + "): " + error.getMessage());
            error.printStackTrace();
        });

        new Thread(sortTask).start();
    }

    private void generateRandomBinaryFile(Path filePath, int count) throws Exception {
        Random rnd = new Random();
        try (DataOutputStream dos = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(filePath)))) {
            for (int i = 0; i < count; i++) {
                dos.writeInt(rnd.nextInt(2000000));
            }
        }
    }

    @FXML
    public void handleExport(ActionEvent event) {
        if (finalBinaryOutput == null || !Files.exists(finalBinaryOutput)) {
            logToScreen("Помилка: Немає результату. Спочатку виконайте сортування!");
            return;
        }

        try {
            long start = Long.parseLong(txtExportStart.getText());
            long count = Long.parseLong(txtExportCount.getText());

            if (start < 0 || count <= 0) {
                logToScreen("Помилка: Некоректний діапазон експорту.");
                return;
            }

            FileChooser fc = new FileChooser();
            fc.setTitle("Зберегти результат експорту");
            fc.setInitialFileName("sorted_result");

            FileChooser.ExtensionFilter txtFilter = new FileChooser.ExtensionFilter("Текстовий файл (*.txt)", "*.txt");
            FileChooser.ExtensionFilter binFilter = new FileChooser.ExtensionFilter("Бінарний файл (*.bin)", "*.bin");
            fc.getExtensionFilters().addAll(txtFilter, binFilter);

            File saveFile = fc.showSaveDialog(txtExportStart.getScene().getWindow());

            if (saveFile != null) {
                String fileName = saveFile.getName().toLowerCase();
                if (fileName.endsWith(".bin")) {
                    DataExporter.exportBinaryToBinary(finalBinaryOutput, saveFile.toPath(), start, count);
                    logToScreen("Бінарний експорт завершено: " + saveFile.getName());
                } else {
                    DataExporter.exportBinaryToText(finalBinaryOutput, saveFile.toPath(), start, count);
                    logToScreen("Текстовий експорт завершено: " + saveFile.getName());
                }
            }
        } catch (NumberFormatException e) {
            logToScreen("Помилка: Введіть коректні числа для діапазону.");
        } catch (Exception e) {
            logToScreen("Помилка експорту: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean isSafeFile(File file) {
        if (file == null || !file.exists()) {
            logToScreen("Помилка: Файл не знайдено.");
            return false;
        }

        String fileName = file.getName().toLowerCase();

        if (fileName.endsWith(".bin")) {
            long size = file.length();
            if (size == 0 || size % 4 != 0) {
                logToScreen("Помилка: Структура .bin файлу пошкоджена. Розмір має бути кратним 4 байтам.");
                return false;
            }
            return true;
        }
        else if (fileName.endsWith(".txt")) {
            return isValidTextFile(file);
        }
        else {
            logToScreen("Помилка: Програма підтримує лише .bin та .txt файли!");
            return false;
        }
    }
    private boolean isValidTextFile(File file) {
        try (java.util.Scanner scanner = new java.util.Scanner(file)) {
            int checkLimit = 100;
            int count = 0;

            while (scanner.hasNext() && count < checkLimit) {
                String token = scanner.next();
                try {
                    Integer.parseInt(token);
                    count++;
                } catch (NumberFormatException e) {
                    logToScreen("Помилка: Текстовий файл містить недопустимі символи ('" + token + "'). Очікуються лише цілі числа");
                    return false;
                }
            }

            if (count == 0) {
                logToScreen("Помилка: Текстовий файл порожній або не містить чисел.");
                return false;
            }

            return true;

        } catch (Exception e) {
            logToScreen("Помилка читання текстового файлу: " + e.getMessage());
            return false;
        }
    }
    private void logToScreen(String message) {
        Platform.runLater(() -> txtLog.appendText(message + "\n"));
    }

}