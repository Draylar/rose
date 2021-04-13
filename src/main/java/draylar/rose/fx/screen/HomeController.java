package draylar.rose.fx.screen;

import javafx.fxml.FXML;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.skin.ScrollPaneSkin;

public class HomeController {

    @FXML
    private ScrollPane content;

    @FXML
    private ScrollBar scrollBar;

    public void initialize() {
        // The standard ScrollPane scrolling is too slow for our application. Speed it up!
        content.getContent().setOnScroll(event -> {
            double delta = event.getDeltaY() * 0.00001;
            content.setVvalue(content.getVvalue() - delta);
        });

        // Locate the hidden scrollbar from our ScrollPane
        content.skinProperty().addListener((observable, oldValue, newValue) -> {
            ScrollBar hiddenScrollBar = ((ScrollPaneSkin) content.getSkin()).getVerticalScrollBar();

            // adjust scroll bar max
            scrollBar.setMax(1);

            // bind our custom scrollbar to the invisible one inside our content pane
            scrollBar.valueProperty().bindBidirectional(hiddenScrollBar.valueProperty());
        });
    }
}
