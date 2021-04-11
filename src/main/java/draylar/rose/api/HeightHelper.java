package draylar.rose.api;

import draylar.rose.Rose;
import javafx.concurrent.Worker;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class HeightHelper {

    private WebView throwaway = new WebView();

    public HeightHelper() {
        // Load standard CSS styling for proper spacing and font sizes
        throwaway.getEngine().setUserStyleSheetLocation(Rose.class.getClassLoader().getResource("style/main.css").toString());
    }

    public CompletableFuture<Integer> getHeight(List<String> elements) {
        CompletableFuture<Integer> ret = new CompletableFuture<>();

        throwaway.getEngine().getLoadWorker().stateProperty().addListener((value, old, newState) -> {
            if(newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) throwaway.getEngine().executeScript("window");
                window.setMember("java", new JavaBridge());
                window.setMember("data", elements.toString().replace("[", "").replace("]", ""));

                // create initial function
                throwaway.getEngine().executeScript(
                        """
                        function getNext() {
                            const div = document.createElement("div");

                            const values = data.split(", ");
                            for(var i = 0; i < values.length; i++) {
                                console.log(values[i]);
                                div.innerHTML += values[i];
                            }

                            document.body.appendChild(div);
                            div.style.height = "";
                            return div.offsetHeight;
                        }
                        """
                );

                ret.complete((int) throwaway.getEngine().executeScript("getNext()"));
            }
        });


        // force-load the webview
        throwaway.getEngine().loadContent("");

        return ret;
    }

    // return a completablefuture that describes a collection of page contents
    public CompletableFuture<String[]> get(String html, double height, double width) {
        CompletableFuture<String[]> ret = new CompletableFuture<>();

        List<String> all = HTMLHelper.getBody(html);

        throwaway.getEngine().getLoadWorker().stateProperty().addListener((value, old, newState) -> {
            if(newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) throwaway.getEngine().executeScript("window");
                window.setMember("java", new JavaBridge());
                window.setMember("allData", String.join("%and%", all)); // TODO: better way to pass a list?
                window.setMember("height", height);
                window.setMember("width", width);
//
//                // override JS logging to redirect to our logger
                throwaway.getEngine().executeScript("console.log = function(message)\n" +
                        "{\n" +
                        "    java.log(message);\n" +
                        "};");
//
//                // test log
                throwaway.getEngine().executeScript("console.log(\"hello, world\");");

                // create initial function
                throwaway.getEngine().executeScript(
                        """
                                var splitData = ""
                                                        
                                function loadData() {
                                    splitData = allData.split("%and%");
                                    
                                    for(var line in splitData) {
                                        console.log("line: " + splitData[line]);
                                    }
                                }
                                                        
                                function getSize(data) { // data is a list of string tags
                                    const div = document.createElement("div");
                                                                
                                    for(var i = 0; i < data.length; i++) {
                                        div.innerHTML += data[i];
                                    }
                                                                
                                    document.body.appendChild(div);
                                    div.style.height = "";
                                    div.style.width = width;
                                    console.log("Height of " + div.innerHTML + " is " + div.getBoundingClientRect().height);
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
                                            console.log(pages[0]);
                                            return pages.join("%page%");
                                        }
                                                                
                                        // Still have elements to process and we are under 500 length. Append it to the current list.
                                        else if (size < height) {
                                            console.log("Appending element: " + element);
                                            currentElements.push(element);
                                        }
                                       
                                        // Over 500 with more elements to go-- push and go again. Do not append the current element to prevent overflow.
                                        else {
                                            pages.push(currentElements.join(""))
                                            console.log("Size: " + size);
                                            currentElements = [] // clear current elements
                                        }
                                    }
                                    
                                    return pages.join("%page%")
                                }
                                
                                // test
                                // const l = []
                                // l[0] = "<p id=\\"auto_bookmark_toc_top\\" class=\\"P__STAR__STAR__STAR__page_break\\"><span><span style=\\" font-weight: bold; font-size: 1.17em;\\">Chapter 1:<br/>The Con Artist Who Claimed to Be a God</span></span></p>",
                                // console.log("yeet");
                                // console.log(getSize(l));
                                """
                );

                throwaway.getEngine().executeScript("console.log(5);");
                throwaway.getEngine().executeScript("loadData();");
                String s = throwaway.getEngine().executeScript("splitIntoPages()").toString();
                String[] result = s.split("%page%");
                ret.complete(result);
            }
        });

        // force-load the webview
        throwaway.getEngine().loadContent("");

        return ret;
    }
}
