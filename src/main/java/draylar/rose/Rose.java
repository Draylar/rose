package draylar.rose;

import draylar.rose.api.Epub;
import draylar.rose.api.JavaBridge;
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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
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
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Rose extends Application {

    public static final Path ROSE_LIBRARY_PATH = Paths.get(System.getProperty("user.home"), "Rose Library");
    public static final Path ROSE_LIBRARY_DATA_PATH = Paths.get(System.getProperty("user.home"), "Rose Library", "Data");
    public static final Logger LOGGER = Logger.getLogger("Rose");
    public static Scene scene;

    private List<Epub> loaded = new ArrayList<>();

    @Override
    public void start(Stage stage) throws Exception {
        initializeRoseLibraryFolder();
        yeet();

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
//        stage.show();
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

    public static void yeet() {
        // calculate pages
        List<String> elements = new ArrayList<>();
        elements.add("<p>hi</p>");
        elements.add("<p>hi</p>");
        elements.add("<p>hi</p>");

        int height = 1300;
        WebView throwaway = new WebView();
        throwaway.getEngine().setUserStyleSheetLocation(Rose.class.getClassLoader().getResource("style/main.css").toString());

        throwaway.getEngine().getLoadWorker().stateProperty().addListener((value, old, newState) -> {
            if(newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) throwaway.getEngine().executeScript("window");
                window.setMember("java", new JavaBridge());
                window.setMember("data", elements.toString());
                window.setMember("height", height);


                // override JS logging to redirect to our logger
                throwaway.getEngine().executeScript("console.log = function(message)\n" +
                        "{\n" +
                        "    java.log(message);\n" +
                        "};");

                // test log
                throwaway.getEngine().executeScript("console.log(\"hello, world\");");

                // create initial function
                throwaway.getEngine().executeScript(
                        """
                        function getNext() {
                            const div = document.createElement("div");
                            
                            const values = data.split(", ");
                            for(var i = 0; i < values.length; i++) {
                                console.log(values[i]);
                                div.innerHTML += values[i];
                            }
                            
                            document.body.appendChild(div);
                            console.log(values.length);
                            console.log("Data: " + data);
                            console.log("innerText: " + div.innerText);
                            console.log("innerHTML: " + div.innerHTML);
                            console.log(window.getComputedStyle(document.body).fontSize);
                            div.style.height = "";
                            return div.offsetHeight;
                        }
                        """
                );

                System.out.println("Height: " + throwaway.getEngine().executeScript("getNext()"));
            }
        });

        throwaway.getEngine().loadContent("");
        throwaway.getEngine().reload();
    }

    public static void open(Epub epub) {
        Parent root = null;

        try {
            root = FXMLLoader.load(Rose.class.getClassLoader().getResource("read.fxml"));
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        scene.setRoot(root);

        // apply epub contents to webview

        scene.getStylesheets().add("style/main.css");
        root.applyCss(); // force load CSS so the following lookup call works (lookups don't work until css is applied)
        WebView view = (WebView) root.lookup("#html");
        scene.getStylesheets().add("style/main.css");

        String content = "";
        for(int i = 0 ; i <= 1_000_0; i++) {
            content += "hiiiiiiiiiiiiiiiiiiiiidddddddihiiiiiiiiiiiiiiiiiiiiidddddddihiiiiiiiiiiiiiiiiiiiiidddddddihiiiiiiiiiiiiiiiiiiiiidddddddi\n";
        }

        view.getEngine().loadContent(content);
        view.getEngine().getLoadWorker().stateProperty().addListener((value, old, newState) -> {
            if(newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) view.getEngine().executeScript("window");
                window.setMember("java", new JavaBridge());

                // override JS logging to redirect to our logger
                view.getEngine().executeScript("console.log = function(message)\n" +
                        "{\n" +
                        "    java.log(message);\n" +
                        "};");

                // test log
                view.getEngine().executeScript("console.log(\"hello, world\");");

                // apply script
                view.getEngine().executeScript("""
                        var desiredHeight = 850;
                        var desiredWidth = 1350;
                        var bodyID = document.getElementsByTagName('body')[0];
                        totalHeight = bodyID.offsetHeight;
                        pageCount = Math.floor(totalHeight/desiredHeight) + 1;
                        bodyID.style.padding = 10; //(optional) prevents clipped letters around the edges
                        bodyID.style.width = desiredWidth * pageCount;
                        bodyID.style.height = desiredHeight;
                        bodyID.style.columnCount = pageCount;
                        console.log(totalHeight + " : " + desiredHeight + " : " + pageCount);
                                """
                );
            }
        });

        root.setOnMouseClicked(event -> {
            if(event.getX() < 100) {
                view.getEngine().executeScript("document.body.scrollLeft -= 1350");
            } else if (event.getX() > scene.getWidth() - 100) {
                view.getEngine().executeScript("document.body.scrollLeft += 1350");
            }
        });
    }

//    public static List<String> getNext(WebEngine throwaway, List<String> elements, int height) {
//
//    }
}
