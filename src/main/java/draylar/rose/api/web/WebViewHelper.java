package draylar.rose.api.web;

import draylar.rose.Rose;
import javafx.scene.web.WebView;

public class WebViewHelper {

    public static WebView from(String html) {
        WebView webView = new WebView();
        webView.getEngine().setUserStyleSheetLocation(Rose.class.getClassLoader().getResource("style/main.css").toString());
        webView.getEngine().loadContent(html);
        webView.getEngine().reload();
        return webView;
    }
}
