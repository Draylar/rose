package draylar.rose;

import draylar.rose.api.Epub;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class RoseTest {

    // TODO: this is IntelliJ/gradle specific. Can we extract from resources directly?
    private static final Path ALICE_IN_WONDERLAND = Paths.get("out/test/resources/alice_in_wonderland.epub");
    private static final String EXPECTED_CONTAINER_XML =
            """
            <?xml version="1.0"?>
            <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
              <rootfiles>
                <rootfile full-path="content.opf" media-type="application/oebps-package+xml"/>
              </rootfiles>
            </container>
            """;

    @Test
    @DisplayName(value = "Test content.opf extraction")
    public void testContentExtraction() {
        Epub epub = new Epub(ALICE_IN_WONDERLAND);
        @Nullable Path result = Epub.findContentOPFPath(epub);

        // The content.opf file should be found and should refer to content.opf.
        Assertions.assertNotNull(result);
        Assertions.assertEquals(ALICE_IN_WONDERLAND.toAbsolutePath().resolve("content.opf"), result);
    }

    @Test
    @DisplayName(value = "Test container.xml extraction")
    public void testContainerExtraction() {
        Epub epub = new Epub(ALICE_IN_WONDERLAND);
        @Nullable String containerXML = epub.readContainerXML();

        // The container.xml file should be found and should equal the expected (manually extracted) container.xml file.
        Assertions.assertNotNull(containerXML);
        Assertions.assertEquals(EXPECTED_CONTAINER_XML, containerXML.replace("\r\n", "\n")); // line-separators are different in the file (crlf vs. lf, so we get rid of them)
    }
}
