package draylar.rose.api;

import draylar.rose.Rose;
import draylar.rose.api.book.ManifestEntry;
import draylar.rose.api.book.SpineEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Represents an .epub book that is in the process of being read from the user's file system.
 */
public class Epub {

    private static final String ROOTFILE_START = "<rootfile full-path=\"";

    private final Path root;
    @Nullable private EpubMetadata metadata;
    @Nullable private BufferedImage coverImage;
    private List<ManifestEntry> manifest;
    private List<SpineEntry> spine;

    /**
     * Constructs a {@link Epub} from the given {@link Path}.
     * @param root .epub file directory represented by this instance
     */
    public Epub(Path root) {
        this.root = root.toAbsolutePath();
        loadMetadata(true);
        extractImages();
    }

    /**
     * Returns the full contents of this .epub's container.xml file.
     *
     * <p>
     * Example container.xml:
     * <pre>
     * {@code
     * <?xml version="1.0" encoding="UTF-8"?>
     * <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
     *     <rootfiles>
     *         <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
     *    </rootfiles>
     * </container>
     * }
     * </pre>
     *
     * @see <a href="https://www.w3.org/publishing/epub3/epub-ocf.html">EPUB open container format</a>
     * @return the container.xml contents, or null if it could not be found/read
     */
    @Nullable
    public String readContainerXML() {
        return read(path -> path.getFileName() != null && path.getFileName().toString().contains("container.xml"));
    }

    @Nullable
    public String readContentOPF() {
        return read(path -> path.getFileName() != null && path.getFileName().toString().contains("content.opf"));
    }

    @Nullable
    public String read(Predicate<Path> predicate) {
        Optional<Path> containerXML = find(predicate).findFirst();

        if(containerXML.isPresent()) {
            try {
                return Files.readString(containerXML.get());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    /**
     * Returns a {@link List} of {@link Path} entries (ZipPath) inside this .epub which match the given predicate.
     *
     * @param predicate predicate to test against all entry paths inside this .epub file
     * @return all elements that match the predicate
     */
    public Stream<Path> find(Predicate<Path> predicate) {
        try {
            for (Path inner : FileSystems.newFileSystem(root, Collections.emptyMap()).getRootDirectories()) {
                if (Files.isDirectory(inner)) {
                    return Files.walk(inner).filter(predicate);
                }
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }

        return Stream.empty();
    }

    /**
     * Extracts the content.opf location from the given .epub's container.xml file.
     *
     * <p>
     * As an example, take the following container.xml file, from C:/book.pdf:
     * <pre>
     * {@code
     * <?xml version="1.0" encoding="UTF-8"?>
     * <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
     *     <rootfiles>
     *         <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
     *    </rootfiles>
     * </container>
     * }
     * </pre>
     *
     * This method will extract the 'OEBPS/content.opf' string and return 'C:/book.pdf/OEBPS/content.opf.
     *
     * @see <a href="https://www.w3.org/publishing/epub3/epub-ocf.html">EPUB open container format</a>
     * @param epub {@link Epub} to read the content.opf file from
     * @return the path of the content.opf file, determined by the container.xml file, relative to the root .epub file, or null if it could not be read
     */
    @Nullable
    public static Path findContentOPFPath(Epub epub) {
        String containerXML = epub.readContainerXML();

        if(containerXML != null) {
            int start = containerXML.indexOf(ROOTFILE_START) + ROOTFILE_START.length();
            int end = containerXML.indexOf("\"", start + 1);
            return Paths.get(epub.root.toString(), containerXML.substring(start, end));
        } else {
            return null;
        }
    }

    public Path getDataDirectory() {
        String fileName = root.getFileName().toString();
        return Paths.get(Rose.ROSE_LIBRARY_DATA_PATH.toString(), fileName.substring(0, fileName.lastIndexOf(".")));
    }

    public Path getImageDirectory() {
        return Paths.get(getDataDirectory().toString(), "Images");
    }

    // time to read image from .epub directly: 161ms
    // time to read after extracting to data directory: 111ms
    /**
     * Returns the cover image from this .epub file as a {@link BufferedImage}.
     *
     * <p>
     * Once this method is called, the {@link BufferedImage} is cached.
     * Future calls will return the cached image directly.
     *
     * @return a {@link BufferedImage} containing the cover image of this .epub, or {@code null} if the cover could not be found/read
     */
    @Nullable
    public BufferedImage readCover() {
        Path coverLocation = getDataDirectory().resolve("cover.png");

        // Do not continue if the cover image already exists.
        if(Files.exists(coverLocation)) {
            return null;
        }

        // Locate the content.opf file from this .epub book.
        Optional<Path> contentOpf = find(path -> path.getFileName() != null && path.getFileName().toString().equals("content.opf")).findFirst();

        // abort mission if the container.opf file was not found
        if(contentOpf.isEmpty()) {
            return null;
        }

        // get contents of content.opf file
        String contentOPF;
        try {
            contentOPF = Files.readString(contentOpf.get());
        } catch (IOException ioException) {
            ioException.printStackTrace();
            return null;
        }

        if(contentOPF != null) {
            // find cover image location
            String coverTag = find(contentOPF, "<meta name=\"cover\" content=\"", "\"");
            if(coverTag != null) {
                List<String> lines = Arrays.asList(contentOPF.split("\n"));
                String coverItem = null;

                // Attempt to search for the cover image location by ID.
                Optional<String> first = lines.stream().filter(line -> line.contains(String.format("id=\"%s\"", coverTag))).findFirst();
                if(first.isPresent()) {
                    String found = first.get();
                    coverItem = find(found, "href=\"", "\"");
                }

                // In some cases, cover elements are tagged with `properties="cover-image"`.
                // If coverItem is null, there is a good chance the cover image is stored in this method.
                if(coverItem == null) {
                    first = lines.stream().filter(line -> line.contains("properties=\"cover-image\"")).findFirst();
                    if(first.isPresent()) {
                        String found = first.get();
                        coverItem = find(found, "href=\"", "\"");
                    }
                }

                if(coverItem != null) {
                    // If the content.opf file is inside a directory, and refers to a file in the same directory, this coverItem path
                    //    will refer to the file relative to content.opf.
                    // To fix this, we prefix the coverItem path with the content.opf path, if it exists.
                    String directory = contentOpf.get().getParent().toString();
                    coverItem = directory + (directory.endsWith("/") ? "" : "/") + coverItem;

                    // Locate the cover image.
                    final String finalCoverItem = coverItem;
                    Optional<Path> imagePath = find(path -> path.toString() != null && path.toString().equals(finalCoverItem)).findFirst();
                    if(imagePath.isPresent()) {
                        // Extract the file to the data directory.
                        try {
                            // TODO: different file extensions?
                            InputStream inputStream = Files.newInputStream(imagePath.get());
                            BufferedImage read = ImageIO.read(inputStream);
                            coverImage = read;
                            return read;
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        System.out.printf("Failed to save cover image for %s.%n", root.getFileName().toString());
        return null;
    }

    public void initializeDataDirectory() {
        Path dataDirectory = getDataDirectory();

        if (!Files.exists(dataDirectory)) {
            try {
                Files.createDirectories(dataDirectory);
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    @Nullable
    private String find(String from, String start, String end) {
        if(!from.contains(start) || !from.contains(end)) {
            return null;
        }

        int startIndex = from.indexOf(start) + start.length();
        int endIndex = from.indexOf(end, startIndex);
        return from.substring(startIndex, endIndex);
    }

    /**
     * Loads the metadata for this .epub.
     *
     * <p>
     * Metadata is loaded from the .epub content.opf file.
     *
     * @param force whether to force-reload metadata, even if cached values were found
     * @return true if the metadata could be loaded, otherwise false
     */
    public boolean loadMetadata(boolean force) {
        if(this.metadata != null && !force) {
            return true;
        }

        // Load content.opf XML file
        @Nullable String content_opf = readContentOPF();

        if(content_opf != null) {
            // Parse XML
            try {
                DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document parsed = builder.parse(new InputSource(new StringReader(content_opf)));
                parsed.normalize();
                this.metadata = EpubMetadata.from(parsed);
            } catch (Exception any) {
                any.printStackTrace();
                return false;
            }
        }

        return false;
    }

    public void loadManifest(boolean force) {
        if(this.manifest != null && !force) {
            return;
        }

        manifest = new ArrayList<>();

        // Load content.opf XML file
        @Nullable String content_opf = readContentOPF();

        if(content_opf != null) {
            // Parse XML
            try {
                DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document parsed = builder.parse(new InputSource(new StringReader(content_opf)));
                parsed.normalize();

                // Grab the manifest tag [<manifest>]
                Element manifestNode = (Element) parsed.getElementsByTagName("manifest").item(0);
                NodeList manifestChildren = manifestNode.getChildNodes();
                for(int i = 0; i < manifestChildren.getLength(); i++) {
                    Node entry = manifestChildren.item(i);

                    if(entry.getNodeType() == Node.ELEMENT_NODE) {
                        String id = entry.getAttributes().getNamedItem("id").getTextContent();
                        String href = entry.getAttributes().getNamedItem("href").getTextContent();
                        String mediaType = entry.getAttributes().getNamedItem("media-type").getTextContent();
                        manifest.add(new ManifestEntry(id, href, mediaType));
                    }
                }
            } catch (Exception any) {
                any.printStackTrace();
            }
        }
    }

    public void loadSpine(boolean force) {
        if(this.spine != null && !force) {
            return;
        }

        spine = new ArrayList<>();

        // Load content.opf XML file
        @Nullable String content_opf = readContentOPF();

        if(content_opf != null) {
            // Parse XML
            try {
                DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document parsed = builder.parse(new InputSource(new StringReader(content_opf)));
                parsed.normalize();

                // Grab the manifest tag [<manifest>]
                Element manifestNode = (Element) parsed.getElementsByTagName("spine").item(0);
                NodeList manifestChildren = manifestNode.getChildNodes();
                for(int i = 0; i < manifestChildren.getLength(); i++) {
                    Node entry = manifestChildren.item(i);

                    if(entry.getNodeType() == Node.ELEMENT_NODE) {
                        String idref = entry.getAttributes().getNamedItem("idref").getTextContent();
                        spine.add(new SpineEntry(idref));
                    }
                }
            } catch (Exception any) {
                any.printStackTrace();
            }
        }
    }

    @NotNull
    public EpubMetadata getMetadata() {
        if(metadata == null) {
            loadMetadata(true);
        }

        return metadata;
    }

    @NotNull
    public List<ManifestEntry> getManifest() {
        if(manifest == null) {
            loadManifest(true);
        }

        return manifest;
    }

    public List<SpineEntry> getSpine() {
        if(spine == null) {
            loadSpine(true);
        }

        return spine;
    }

    public int getSpineCount() {
        return getSpine().size();
    }

    /**
     * @param index index to retrieve page from
     * @return the spine entry at the given index (zero-indexed)
     * @throws IndexOutOfBoundsException if the index is greater than the number of spine entries or less than 0
     */
    public SpineEntry getSpineEntry(int index) {
        if(index < 0) {
            throw new IndexOutOfBoundsException();
        }

        if(index >= getSpineCount()) {
            throw new IndexOutOfBoundsException();
        }

        return getSpine().get(index);
    }

    public String readSection(SpineEntry spineEntry) {
        Optional<ManifestEntry> first = getManifest().stream().filter(entry -> entry.getId().equals(spineEntry.getIdref())).findFirst();

        // If a manifest entry was found that matches the given spine entry, read the contents and return it.
        if(first.isPresent()) {
            return read(path -> path.toString().contains(first.get().getHref()));
        }

        return "";
    }

    public void extractTemporaryImages() {

    }

    /**
     * Extracts the image contents of this .epub to a temporary directory.
     *
     * <p>
     * While most operations (reading cover images, loading pages, and getting order) can be done without opening the jar,
     *  images loaded in HTML files will not properly load, and there is no sane way to bypass this while keeping
     *  the images in the jar without replacing the paths at runtime and only extracting the images.
     */
    private void extractImages() {
        // TODO: other image types?
        find(path -> path.toString().endsWith(".png") || path.toString().endsWith(".jpg")).forEach(path -> {
            new Thread(() -> {
                try {
                    String imagesPath = getImageDirectory().toString();
                    Files.createDirectories(Paths.get(imagesPath));
                    Path target = Paths.get(imagesPath, path.getFileName().toString());
                    if(!Files.exists(target)) {
                        Files.copy(path, target);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        });
    }

    @Nullable
    public BufferedImage getCoverImage() {
        return coverImage;
    }
}
