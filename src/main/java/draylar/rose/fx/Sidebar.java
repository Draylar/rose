package draylar.rose.fx;

import draylar.rose.api.Epub;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import org.imgscalr.Scalr;

import java.awt.image.BufferedImage;

public class Sidebar extends VBox {

    public Sidebar() {
        setId("sidebarContent");
        setAlignment(Pos.TOP_CENTER);
        HBox.setHgrow(this, Priority.ALWAYS);
    }

    /**
     * Refreshes this sidebar to display the given {@link Epub}'s information.
     *
     * <p>
     *
     * @param epub
     */
    public void display(Epub epub) {
        getChildren().clear();
        setAlignment(Pos.TOP_CENTER);

        // generic image
        BufferedImage resized = Scalr.resize(epub.getCoverImage(), 250, 250);
        ImageView image = new ImageView(SwingFXUtils.toFXImage(resized, null));
        image.setFitHeight(250.0);
        image.setFitWidth(200);
        image.setPickOnBounds(true);
        image.setPreserveRatio(true);
        VBox.setMargin(image, new Insets(50.0, 0.0, 25.0, 0.0));

        // label
        Label title = new Label(epub.getMetadata().getTitle());
        title.setFont(Font.font("Segoe UI", 20));
        title.setWrapText(true);
        title.setTextAlignment(TextAlignment.CENTER);
        VBox.setMargin(title, new Insets(0, 25.0, 0.0, 25.0));

        // author
        Label author = new Label(epub.getMetadata().getCreator());
        author.setFont(Font.font("Segoe UI", 20));
        author.setWrapText(true);
        author.setTextAlignment(TextAlignment.CENTER);
        VBox.setMargin(author, new Insets(20.0, 25.0, 0.0, 25.0));

        // add nodes
        getChildren().addAll(image, title, author);
    }

    /**
     * Resets this sidebar to the default sidebar content.
     *
     * <p>
     * The default content includes a welcome message and link to https://github.com/Draylar/rose.
     */
    public void clear() {
        getChildren().clear();

        // top "Rose" label
        Label title = new Label("Rose 1.0.0");
        title.setId("title");
        title.setFont(Font.font("Segoe UI", 20));
        VBox.setMargin(title, new Insets(75.0, 0.0, 0.0, 0.0));

        // generic description
        Label description = new Label("Thank you for using Rose! Enjoy your reading session.");
        description.setId("description");
        description.setFont(Font.font("Segoe UI", 20));
        description.setWrapText(true);
        VBox.setMargin(description, new Insets(15.0, 25.0, 0.0, 25.0));

        // spacing
        Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);

        // bottom GitHuib credit
        Label github = new Label("github.com/Draylar/rose");
        github.setId("github");
        github.setFont(Font.font("Segoe UI", 20));
        github.setAlignment(Pos.BOTTOM_CENTER);
        github.setUnderline(true);
        github.setStyle("-fx-cursor: hand;");
        VBox.setMargin(github, new Insets(0, 0, 25, 0));

        // add
        getChildren().addAll(title, description, region, github);
    }
}
