package draylar.rose.api;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class EpubMetadata {

    private final String title;
    private final String creator;
    private final String contributor;
    private final String identifier;
    private final String relation;
    private final String publisher;
    private final String date;
    private final String subject;
    private final String language;

    public EpubMetadata(
            String title,
            String creator,
            String contributor,
            String identifier,
            String relation,
            String publisher,
            String date,
            String subject,
            String language) {
        this.title = title;
        this.creator = creator;
        this.contributor = contributor;
        this.identifier = identifier;
        this.relation = relation;
        this.publisher = publisher;
        this.date = date;
        this.subject = subject;
        this.language = language;
    }

    public static EpubMetadata from(Document content) {
        Element metadata = (Element) content.getElementsByTagName("metadata").item(0);
        String title = retrieve(metadata, "dc:title");
        String creator = retrieve(metadata, "dc:creator");
        String contributor = retrieve(metadata, "dc:contributor");
        String identifier = retrieve(metadata, "dc:identifier");
        String relation = retrieve(metadata, "dc:relation");
        String publisher = retrieve(metadata, "dc:publisher");
        String date = retrieve(metadata, "dc:date");
        String subject = retrieve(metadata, "dc:subject");
        String language = retrieve(metadata, "dc:language");
        return new EpubMetadata(title, creator, contributor, identifier, relation, publisher, date, subject, language);
    }

    private static String retrieve(Element metadata, String tag) {
        Node item = metadata.getElementsByTagName(tag).item(0);
        return item == null ? "" : item.getTextContent();
    }

    public String getTitle() {
        return title;
    }

    public String getCreator() {
        return creator;
    }

    public String getContributor() {
        return contributor;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getRelation() {
        return relation;
    }

    public String getPublisher() {
        return publisher;
    }

    public String getDate() {
        return date;
    }

    public String getSubject() {
        return subject;
    }

    public String getLanguage() {
        return language;
    }
}
