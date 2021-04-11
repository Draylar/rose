package draylar.rose.api;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSSerializer;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class HTMLHelper {

    // TODO: this method assumes the given HTML is valid.
    /**
     * @return a list of body elements from the given HTML document text.
     */
    public static List<String> getBody(String html) {
        try {
            List<String> ret = new ArrayList<>();

            // First, locate the <body> content inside the given HTML document.
            String startTag = getStartBodyTag(html);
            String bodyText = html.substring(html.indexOf(startTag), html.lastIndexOf("</body>") + "</body>".length());

            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document parsed = builder.parse(new InputSource(new StringReader(bodyText)));
            parsed.normalize();
            Node body = parsed.getElementsByTagName("body").item(0);

            // source: https://stackoverflow.com/questions/3300839/get-a-nodes-inner-xml-as-string-in-java-dom
            DOMImplementationLS lsImpl = (DOMImplementationLS) body.getOwnerDocument().getImplementation().getFeature("LS", "3.0");
            LSSerializer lsSerializer = lsImpl.createLSSerializer();
            lsSerializer.getDomConfig().setParameter("xml-declaration", false);
            NodeList childNodes = body.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                String e = lsSerializer.writeToString(childNodes.item(i));

                // parsing is dumb, and most lines have skewed whitespace.
                // for more reliable testing, we will simply strip all whitespace on each line
                String result = Arrays.stream(e.split("\n"))
                        .map(String::trim)
                        .filter(line -> !line.isBlank())
                        .collect(Collectors.joining("\n"));

                // Append the trimmed result to the list
                if(!result.isBlank()) {
                    ret.add(result);
                }
            }
            // end source

            return ret;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Collections.emptyList();
    }

    public static String getTemplate(String html) {
        String startTag = getStartBodyTag(html);
        int endIndex = html.indexOf(startTag) + startTag.length();
        int restartIndex = html.lastIndexOf("</body>");
        return html.substring(0, endIndex) + "%s" + html.substring(restartIndex);
    }

    public static String getStartBodyTag(String html) {
        int index = html.indexOf("<body");

        // if '<body' was found, begin operation
        if(index != -1) {
            int end = html.indexOf(">", index);
            return html.substring(index, end + 1);
        }

        return "<body>";
    }
}
