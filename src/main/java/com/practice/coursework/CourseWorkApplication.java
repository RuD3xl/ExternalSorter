package com.practice.coursework;

import com.practice.coursework.ui.GUIApplication;
import javafx.application.Application;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.PrintStream;

@SpringBootApplication(scanBasePackages = "com.practice.coursework")
public class CourseWorkApplication {
    public static void main(String[] args) {
        java.util.logging.LogManager.getLogManager().reset();
        java.util.logging.Logger.getLogger("").setLevel(java.util.logging.Level.SEVERE);
        Application.launch(GUIApplication.class, args);
    }
}