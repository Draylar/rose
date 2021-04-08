package draylar.rose;

import draylar.rose.api.Epub;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ImageExtractionTest {

    private static final Path THE_YOUNGEST_CAMEL = Paths.get("out/test/resources/the_youngest_camel.epub");

    @Test
    public void testImageExtractionSpeed() {
        Epub epub = new Epub(THE_YOUNGEST_CAMEL);
        BufferedImage bufferedImage = epub.readCover();
        Assertions.assertNotNull(bufferedImage);
    }
}
