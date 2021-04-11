package draylar.rose;

import draylar.rose.api.Epub;
import draylar.rose.api.HTMLHelper;
import draylar.rose.api.HeightHelper;
import draylar.rose.api.JavaBridge;
import draylar.rose.api.web.WebViewHelper;
import draylar.rose.fx.BookIconNode;
import draylar.rose.fx.Sidebar;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Rose extends Application {

    public static final Path ROSE_LIBRARY_PATH = Paths.get(System.getProperty("user.home"), "Rose Library");
    public static final Path ROSE_LIBRARY_DATA_PATH = Paths.get(System.getProperty("user.home"), "Rose Library", "Data");
    public static final Logger LOGGER = Logger.getLogger("Rose");
    public static Scene scene;
    public static final List<WebView> pages = new ArrayList<>();

    private List<Epub> loaded = new ArrayList<>();

    @Override
    public void start(Stage stage) throws Exception {
        initializeRoseLibraryFolder();

        // initialize UI
        Parent root = FXMLLoader.load(getClass().getClassLoader().getResource("root.fxml"));
        scene = new Scene(root, 1350, 900);
        stage.setTitle("Rose");
        stage.setMinWidth(500);
        stage.setScene(scene);
        scene.getStylesheets().add("style/main.css");

        // load files
        List<Path> books = Files.list(ROSE_LIBRARY_PATH)
                .filter(path -> !Files.isDirectory(path))
                .filter(path -> path.toString().endsWith(".epub"))
                .collect(Collectors.toList());

        // find the general book section in the fxml file
        scene.getRoot().applyCss(); // force load CSS so the following lookup call works (lookups don't work until css is applied)
        FlowPane allBooks = (FlowPane) scene.lookup("#allBooks");
        FlowPane recentBooks = (FlowPane) scene.lookup("#recentBooks");

        // retrieve sidebar
        HBox sidebarContent = (HBox) scene.lookup("#sidebar");
        Sidebar sidebar = new Sidebar();
        sidebar.clear();
        sidebarContent.getChildren().add(0, sidebar);

        // Each .epub file should have a directory associated with it for metadata & thumbnail cache.
        // These directories are found in /Rose Library/Data. Each data directory has the same filename as the .epub file.
        books.forEach(path -> {
            // Each book is loaded on a separate thread, this DRASTICALLY decreases load time
            new Thread(() -> {
                Epub epub = new Epub(path);
                epub.initializeDataDirectory();
                epub.readCover();

                // Attempt to initialize the epub with its content.opf file.
                // If it was not found, log an error and continue to the next book.
                boolean result = epub.loadMetadata(false);
                if (result) {
                    loaded.add(epub);

                    // add epub book
                    Platform.runLater(() -> {
                        BookIconNode node = new BookIconNode(epub);
                        allBooks.getChildren().add(node);

                        // makeshift recent section
                        if(loaded.indexOf(epub) < 6) {
                            recentBooks.getChildren().add(node);
                        }

                        // on click, open the book
                        node.setOnMouseClicked(event -> {
                            sidebar.display(node.getBook()); // ??????
                        });
                    });
                } else {
                    // TODO: still add book, but have invalid cover/warning marker on it?
                    LOGGER.warning(String.format("content.opf could not be read from %s. Is the file a valid .epub? Skipping to the next book.", path.getFileName()));
                }
            }).start();
        });

        // The standard ScrollPane scrolling is too slow for our application. Speed it up!
        ScrollPane scrollable = (ScrollPane) scene.lookup("#scrollableContent");
        scrollable.getContent().setOnScroll(event -> {
            double delta = event.getDeltaY() * 0.01;
            scrollable.setVvalue(scrollable.getVvalue() - delta);
        });

        // load
        stage.show();
    }

    public void extractContentFile(Path from, Path to) {
        try {
            FileSystems.newFileSystem(from, Collections.emptyMap())
                    .getRootDirectories()
                    .forEach(root -> {
                        try {
                            // Iterate through the .epub file (from) and locate the content.opf file.
                            // Extract it to the data directory (to).
                            for (Path path : Files.walk(root)
                                    .filter(path -> path.getFileName() != null)
                                    .filter(path -> path.getFileName().toString().equals("content.opf"))
                                    .collect(Collectors.toList())) {
                                Files.copy(path, Paths.get(to.toString(), "content.opf"));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    /**
     * Attempts to crate the Rose Library directory if it does not already exist.
     *
     * <p>
     * By default, this directory is found at C:/Users/x/Rose Library.
     */
    public static void initializeRoseLibraryFolder() {
        if (!Files.exists(ROSE_LIBRARY_PATH)) {
            try {
                Files.createDirectories(ROSE_LIBRARY_PATH);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    public static void open(Epub epub) {
        BorderPane root = null;

        // Attempt to load the read.fxml file.
        // TODO: error handling/response for the user to see if something goes wrong?
        try {
            root = FXMLLoader.load(Rose.class.getClassLoader().getResource("read.fxml"));
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        // Update the root of the Rose JavaFX window to display our epub content.
        scene.setRoot(root);

        // get vbox from display scene
        root.applyCss();

        /*

         When the .epub is loaded, we need to create a list of WebView, each of which describe a single page inside the application.

         Potential alternatives:
            - Display all HTML in a free-scrolling vertical document. For paging, lock the scroll bar and have clicks move the document down.
                  Advantages:
                      - Most "native" solution for .epub files, which are already websites
                  Disadvantages:
                      - Books are horizontal. Horizontal page-flip animations would not be possible.
                      - No page-breaks with skipping unless we injected JS for moving to the next page

            - Use CSS to display elements horizontally with breaks
                   Advantages:
                       - Already implemented
                   Disadvantages:
                       - Hard to disable scroll and side behavior
                       - No concept of pages on the Java side
        */

        List<WebView> pages = new ArrayList<>();

        // Find the HTML document.
        // TODO: more than 1 spine entry
        String html = epub.readSection(epub.getSpineEntry(6));
        String template = HTMLHelper.getTemplate(html);
        HeightHelper helper = new HeightHelper();
        pages.clear();
        CompletableFuture<String[]> completableFuture = helper.get(html, root.getHeight() * .95f, root.getWidth() * .8);
        BorderPane finalRoot = root;
        completableFuture.thenAccept(result -> {
            for(String page : result) {
                pages.add(WebViewHelper.from(String.format(template, page)));
            }

            // setup first page
            if(!pages.isEmpty()) {
                finalRoot.setCenter(pages.get(0));
            }
        });

        root.setOnKeyPressed(key -> {
            if(key.getCode().equals(KeyCode.LEFT)) {
                finalRoot.setCenter(pages.get(Math.max(0, pages.indexOf(finalRoot.getCenter()) - 1)));
            } else if (key.getCode().equals(KeyCode.RIGHT)) {
                finalRoot.setCenter(pages.get(Math.min(pages.size() - 1, pages.indexOf(finalRoot.getCenter()) + 1)));
            }
        });
    }
}
