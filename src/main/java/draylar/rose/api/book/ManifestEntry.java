package draylar.rose.api.book;

public class ManifestEntry {

    private final String id;
    private final String href;
    private final String mediaType;

    public ManifestEntry(String id, String href, String mediaType) {
        this.id = id;
        this.href = href;
        this.mediaType = mediaType;
    }

    public String getId() {
        return id;
    }

    public String getHref() {
        return href;
    }

    public String getMediaType() {
        return mediaType;
    }
}
