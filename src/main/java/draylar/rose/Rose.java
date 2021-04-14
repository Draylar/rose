package draylar.rose;

import draylar.rose.api.Epub;
import draylar.rose.api.HTMLHelper;
import draylar.rose.api.HeightHelper;
import draylar.rose.api.book.SpineEntry;
import draylar.rose.api.web.WebViewHelper;
import draylar.rose.fx.BookIconNode;
import draylar.rose.fx.Sidebar;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Pair;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Rose extends Application {

    public static final Path ROSE_LIBRARY_PATH = Paths.get(System.getProperty("user.home"), "Rose Library");
    public static final Path ROSE_LIBRARY_DATA_PATH = Paths.get(System.getProperty("user.home"), "Rose Library", "Data");
    public static Parent home;
    public static Scene scene;
    public static int page = 0;
    public static final List<WebView> pages = new ArrayList<>();

    private List<Epub> loaded = new ArrayList<>();

    @Override
    public void start(Stage stage) throws Exception {
        initializeRoseLibraryFolder();

        // initialize UI
        home = FXMLLoader.load(getClass().getClassLoader().getResource("root.fxml"));
        scene = new Scene(home, 1350, 900);
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
                    System.out.println(String.format("content.opf could not be read from %s. Is the file a valid .epub? Skipping to the next book.", path.getFileName()));
                }
            }).start();
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

    public static void home() {
        scene.setRoot(home);
    }

    public static void open(Epub epub) {
        GridPane root = null;

        // Attempt to load the read.fxml file.
        // TODO: error handling/response for the user to see if something goes wrong?
        try {
            root = FXMLLoader.load(Rose.class.getClassLoader().getResource("read.fxml"));
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        // Update the root of the Rose JavaFX window to display our epub content.
        scene.setRoot(root);

       // Force-apply CSS so our search-by-id operations work later on
        root.applyCss();

        // Keep track of all pages & all futures
        List<WebView> pages = new ArrayList<>();
        List<CompletableFuture<Pair<SpineEntry, String[]>>> futures = new ArrayList<>();
        GridPane finalRoot = root;

        // Start timer
        long start = System.currentTimeMillis();

        // For each TOC entry in this epub, calculate pages...
        epub.getSpine().forEach(entry -> {
            String html = epub.readSection(entry);

            // HTML files have src/image tags that reference images from their perspective/directory.
            // Because our HTML file is ""moved"", the references do not link to images properly.
            // To fix this, we reference saved images which were extracted earlier in the loading pipeline.
            // Each src attribute is replaced with a src reference to the same local file in the data directory.
            Pattern pattern = Pattern.compile("src=\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(html);
            html = matcher.replaceAll(result -> {
                String group = result.group(1);
                URI x = Paths.get(epub.getImageDirectory().toString(), group.substring(group.lastIndexOf("/") + 1)).toUri();
                return String.format("src=\"%s\"", x);
            });

            // Retrieve the template (HTML without the body) from the current spine entry.
            String template = HTMLHelper.getTemplate(html);

            // Create a task to calculate the pages from our HTML.
            // This future is stored in a list so we can reference it later.
            HeightHelper helper = new HeightHelper();
            CompletableFuture<Pair<SpineEntry, String[]>> future = helper.calculatePages(entry, html, finalRoot.getHeight() * .85f, finalRoot.getWidth() * .6);
            futures.add(future);

            // Debug log
            System.out.printf("Loading %s.\n", entry.getIdref());

            // When the future is finished calculating the pages for this particular entry in the book,
            //   we iterate over each page and add a WebView element representing the page to our screen.
            // Additional setup for individual pages also occurs here.
            future.thenAccept(result -> {
                for(String page : result.getValue()) {
                    // TODO: this will break very heavily as soon as a book has %s in it
                    WebView from = WebViewHelper.from(template.replace("%s", page));
                    from.maxWidthProperty().bind(finalRoot.widthProperty().multiply(.6));
                    pages.add(from);
                }

                // If the SpineEntry is the first one in this epub's TOC, load the first page now.
                if(epub.getSpine().indexOf(result.getKey()) == 0 && !pages.isEmpty()) {
                    finalRoot.add(pages.get(0), 1, 0);
                }

                System.out.printf("%s has loaded! Time taken: " + (System.currentTimeMillis() - start) + "ms%n", result.getKey().getIdref());
            }).exceptionally(error -> {
                error.printStackTrace();
                return null;
            });

            // Setup arrow-key click events for traversing through pages.
            finalRoot.setOnKeyPressed(key -> {
                // Left-key => go one page back
                if(key.getCode().equals(KeyCode.LEFT)) {
                    finalRoot.getChildren().remove(1);
                    page = Math.max(0, page - 1);
                    finalRoot.add(pages.get(page), 1, 0);
                }

                // Right-key => go one page forwards
                else if (key.getCode().equals(KeyCode.RIGHT)) {
                    finalRoot.getChildren().remove(1);
                    page = Math.min(pages.size() - 1, page + 1);
                    finalRoot.add(pages.get(page), 1, 0);
                }

                key.consume();
            });
        });

        // Create a future that depends on all entries completing.
        // Once this future is done, we log a message.
        CompletableFuture<Void> finished = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        finished.thenAccept(unused -> {
            System.out.println("All sections have loaded! Time taken: " + (System.currentTimeMillis() - start) + "ms");
        });
    }
}
