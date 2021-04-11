package draylar.rose;

import draylar.rose.api.HTMLHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

public class HTMLHelperTest {

    private static final String EXAMPLE_HTML = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta http-equiv="X-UA-Compatible" content="IE=edge">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Document</title>
            </head>
            <body>
                <div id="test">
                    <p>hi</p>
                </div>
                        
                <p>Hello, world!</p>
                
                <img id="CoverImage" src="../Images/cover.jpg" class="Cover" alt="Cover"/>
            </body>
            </html>
            """;

    @Test
    public void testHTMLHelper() {
        List<String> elements = HTMLHelper.getBody(EXAMPLE_HTML);
        Assertions.assertEquals(3, elements.size());

        // Check for nested elements inside divs
        Assertions.assertEquals("""
                <div id="test">
                <p>hi</p>
                </div>"""
                , elements.get(0).replace("\r\n", "\n"));

        // Check standard paragraph after a line break
        Assertions.assertEquals("<p>Hello, world!</p>", elements.get(1));

        // IMG with various attributes.
        // java w3c XML processing does not retain attribute order, so we split and check that all elements exist instead
        List<String> coverTag = Arrays.asList(elements.get(2).replace("/>", "").split(" "));
        Assertions.assertTrue(
                coverTag.contains("alt=\"Cover\"") &&
                        coverTag.contains("class=\"Cover\"") &&
                        coverTag.contains("id=\"CoverImage\"") &&
                        coverTag.contains("src=\"../Images/cover.jpg\""));
    }

    @Test
    public void testBodyTagDetection() {
        Assertions.assertEquals("<body>", HTMLHelper.getStartBodyTag("jasdmnsanrn<body>sadnanrewajr"));
        Assertions.assertEquals("<body tag=\"hi\">", HTMLHelper.getStartBodyTag("jasdmnsanrn<body tag=\"hi\">sadnanrewajr"));
    }
}
