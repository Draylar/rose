package draylar.rose;

import draylar.rose.api.Epub;
import draylar.rose.api.EpubMetadata;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

public class MetadataTest {

    private static final Path THE_YOUNGEST_CAMEL = Paths.get("out/test/resources/the_youngest_camel.epub");

    @Test
    public void testMetadataExtraction() {
        Epub epub = new Epub(THE_YOUNGEST_CAMEL);
        epub.loadMetadata(true);
        EpubMetadata metadata = epub.getMetadata();

        Assertions.assertEquals("The Youngest Camel", metadata.getTitle());
        Assertions.assertEquals("en", metadata.getLanguage());
        Assertions.assertEquals("Kay Boyle", metadata.getCreator());
        Assertions.assertEquals("Fritz Kredel", metadata.getContributor());
        Assertions.assertEquals("2021-04-04", metadata.getDate());

    }
}
