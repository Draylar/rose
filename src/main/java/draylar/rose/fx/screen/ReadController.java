package draylar.rose.fx.screen;

import draylar.rose.Rose;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.GridPane;

public class ReadController {

    @FXML
    private GridPane root;

    @FXML
    private Button homeButton;

    public void initialize() {
        // When the home button is clicked, swap back to the main application screen.
        homeButton.setOnMouseClicked(event -> Rose.home());
    }
}
