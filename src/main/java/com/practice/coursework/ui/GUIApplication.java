package com.practice.coursework.ui;

import com.practice.coursework.CourseWorkApplication;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.net.URL;

public class GUIApplication extends Application {
    private ConfigurableApplicationContext springContext;

    @Override
    public void init() {

        springContext = new SpringApplicationBuilder(CourseWorkApplication.class)
                .headless(false)
                .run(getParameters().getRaw().toArray(new String[0]));
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        URL fxmlLocation = getClass().getResource("/fxml/MainWindow.fxml");
        FXMLLoader loader = new FXMLLoader(fxmlLocation);

        loader.setControllerFactory(springContext::getBean);

        Parent root = loader.load();
        primaryStage.setTitle("SortApp");
        primaryStage.setScene(new Scene(root, 650, 600));
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    @Override
    public void stop() {
        springContext.close();
        Platform.exit();
    }
}