package draylar.rose.api;

import draylar.rose.Rose;
import draylar.rose.api.book.SpineEntry;
import javafx.concurrent.Worker;
import javafx.scene.web.WebView;
import javafx.util.Pair;
import netscape.javascript.JSObject;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class HeightHelper {

    private final static WebView throwaway = new WebView();

    public HeightHelper() {
        throwaway.getEngine().setUserStyleSheetLocation(Rose.class.getClassLoader().getResource("style/main.css").toString());
    }

    /**
     * Returns a {@link CompletableFuture} providing a {@code String[]}, where each element represents a single page from the provided HTML content.
     *
     * <p>
     * Each page will approximately span the height of the screen, but will not surpass it.
     *
     * @param entry current TOC entry being operated on for return context
     * @param html full HTML content to process into pages
     * @param height height of each page
     * @param width width of each page
     * @return  a {@link CompletableFuture} that provides pages derived from the passed in HTML document
     */
    public CompletableFuture<Pair<SpineEntry, String[]>> calculatePages(SpineEntry entry, String html, double height, double width) {
        CompletableFuture<Pair<SpineEntry, String[]>> ret = new CompletableFuture<>();
        List<String> bodyElements = HTMLHelper.getBody(html);

        throwaway.getEngine().getLoadWorker().stateProperty().addListener((value, old, newState) -> {
            if(newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) throwaway.getEngine().executeScript("window");
                window.setMember("java", new JavaBridge());
                window.setMember("allData", String.join("%and%", bodyElements)); // TODO: better way to pass a list?
                window.setMember("height", height);
                window.setMember("width", width);

                // Override the standard JavaScript log method to redirect to our Java Bridge.
                throwaway.getEngine().executeScript("console.log = function(message)\n" +
                        "{\n" +
                        "    java.log(message);\n" +
                        "};");

                // Define the JS functions for calculating pages from passed in HTML content.
                throwaway.getEngine().executeScript(
                        """
                                var splitData = ""
                                                        
                                function loadData() {
                                    splitData = allData.split("%and%");
                                }
                                                        
                                function getSize(data) { // data is a list of string tags
                                    const div = document.createElement("div");
                                                                
                                    for(var i = 0; i < data.length; i++) {
                                        div.innerHTML += data[i];
                                    }
                                                                
                                    document.body.appendChild(div);
                                    div.style.height = "";
                                    div.style.width = width;
                                    return div.offsetHeight;
                                }
                                                        
                                function splitIntoPages() {
                                    const pages = [];
                                   
                                    // We currently have a list of ALL body tags in an .html document,
                                    // and need to split them up into pages.
                                    // To accomplish this, we loop over the documents, adding elements one-by-one until we hit a max size.
                                    // After the max size is hit, the page is stored, and the next elements are added.
                                   
                                    var currentElements = []
                                    while(splitData.length !== 0) {
                                        // test pushing item onto the array
                                        // if it overflows, we know this page is done.
                                        // otherwise, save the change
                                        const copy = [...currentElements];
                                        const element = splitData[0];
                                        copy.push(element);
                                        const size = getSize(copy)
                                                                
                                        splitData.splice(0, 1);
                                                                
                                        // The size of the elements is less than 500. We can append to the current element.
                                        // This was the last element. Return it now!
                                        if(splitData.length == 0) {
                                            // TODO: overflow on the last page?
                                            currentElements.push(element);
                                            pages.push(currentElements.join(""))
                                            return pages.join("%page%");
                                        }
                                                                
                                        // Still have elements to process and we are under 500 length. Append it to the current list.
                                        else if (size < height) {
                                            
                                            // Images are always on a new page, because offsetHeight does not properly return their height
                                            if(element.includes("img")) {
                                                console.log("found");
                                                
                                                // no elements so far, stop the page here
                                                if(currentElements.length == 0) {
                                                    currentElements.push(element);
                                                    pages.push(currentElements.join(""));
                                                    currentElements = []
                                                }
                                                
                                                // already has an element, save that as a page and keep going
                                                else {
                                                    pages.push(currentElements.join(""));
                                                    currentElements = []
                                                    splitData.unshift(element); // re-add element for next iteration
                                                }
                                                
                                            } else {
                                                currentElements.push(element);
                                            }
                                        }
                                       
                                        // Over 500 with more elements to go-- push and go again. Do not append the current element to prevent overflow.
                                        else {
                                            pages.push(currentElements.join(""))
                                            currentElements = [] // clear current elements
                                        }
                                    }
                                    
                                    return pages.join("%page%")
                                }
                                """
                );

                throwaway.getEngine().executeScript("loadData();");
                String s = throwaway.getEngine().executeScript("splitIntoPages()").toString();
                String[] result = s.split("%page%");
                ret.complete(new Pair<>(entry, result));
            }
        });

        // force-load the webview
        throwaway.getEngine().loadContent("");

        return ret;
    }
}
