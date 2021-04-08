package draylar.rose.fx;

import draylar.rose.Rose;
import draylar.rose.api.Epub;
import draylar.rose.api.EpubMetadata;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.imgscalr.Scalr;

import java.awt.image.BufferedImage;
import java.util.Random;

public class BookIconNode extends VBox {

    private final ImageView coverImageView;
    private final Epub book;

    public BookIconNode(Epub book) {
        this.book = book;
        BufferedImage image = book.getCoverImage();
        EpubMetadata metadata = book.getMetadata();

        // JavaFX ImageView downscaling is very bad.
        // To get around this, we manually downscale our images using imgscalr (https://github.com/rkalla/imgscalr).
        if (image != null) {
            image = Scalr.resize(image, 95, 142);
        }

        // Initialize book cover properties if it was properly loaded.
        if (image != null) {
            this.coverImageView = new ImageView(SwingFXUtils.toFXImage(image, null));
        } else {
            // TODO: in the future, this should load in an invalid cover image (such as a red book with an 'X' through it).
            this.coverImageView = new ImageView();
        }

        // setup image properties
        this.coverImageView.setPreserveRatio(true);
        this.coverImageView.setFitWidth(200);
        this.coverImageView.setFitHeight(142);
        this.coverImageView.setSmooth(true);

        initializeHoverHandlers();
        initializeOpenHandler();

        // Add the cover view image to 'this' VBox as the first/top element.
        getChildren().add(coverImageView);
        this.coverImageView.setStyle("-fx-cursor: hand;");

        // add title & author underneath image
        if (metadata != null) {
            Label e = new Label(metadata.getTitle());
            e.setWrapText(true);
            getChildren().add(e);
        }

        this.maxWidthProperty().bind(coverImageView.fitWidthProperty().multiply(.70f));

        // cursor on hover
//        setStyle("-fx-background-color: #" + new Random().nextInt(1_000_000) + ";");
    }

    // When the ImageView (cover/icon) of this book icon node is hovered over,
    //   it slightly expands for emphasis.
    public void initializeHoverHandlers() {
        ScaleTransition scaleTransition = new ScaleTransition(Duration.millis(200), coverImageView);
        scaleTransition.setFromX(1);
        scaleTransition.setFromY(1);
        scaleTransition.setToX(1.1);
        scaleTransition.setToY(1.1);
        scaleTransition.setInterpolator(Interpolator.EASE_BOTH);

        ScaleTransition reversed = new ScaleTransition(Duration.millis(200), coverImageView);
        reversed.setFromX(1.1);
        reversed.setFromY(1.1);
        reversed.setToX(1);
        reversed.setToY(1);
        reversed.setInterpolator(Interpolator.EASE_BOTH);

        setOnMouseEntered(event -> {
            scaleTransition.play();
        });

        setOnMouseExited(event -> {
            reversed.play();
        });
    }

    public void initializeOpenHandler() {
        coverImageView.setOnMouseClicked(event -> {
            if(event.getClickCount() == 2) {
                Rose.open(book);
            }
        });
    }

    public Epub getBook() {
        return book;
    }
}
