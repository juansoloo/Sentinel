package MVC.Controllers;

import MVC.Views.RepeaterView;
import Proxy.HttpMessageFormatter;
import Proxy.HttpMessageParser;
import Proxy.ProxyResponse;
import Proxy.ProxyRequest;
import Proxy.RepeaterClient;

import java.io.IOException;

public class RepeaterController {
    private final RepeaterView repeaterView;
    private final RepeaterClient repeaterClient;

    public RepeaterController(RepeaterView repeaterView, RepeaterClient repeaterClient) {
        this.repeaterView = repeaterView;
        this.repeaterClient = repeaterClient;
    }
    
    public void loadRequest(ProxyRequest req) {
        if (req == null) {
            return;
        }

        String rawRequest = HttpMessageFormatter.renderRequest(req);
        repeaterView.loadRequest(rawRequest);
    }

    public void onSend() {
        String rawRequest = repeaterView.getRequestText();

        ProxyRequest req = HttpMessageParser.parseRequest(rawRequest);

        if (req == null) {
            repeaterView.displayResponse("Invalid request. Check the request line, Host header, and header/body separator.");
            return;
        }

        try {
            ProxyResponse response = repeaterClient.send(req);
            repeaterView.displayResponse(HttpMessageFormatter.renderResponse(response));
        } catch (IllegalArgumentException e) {
            repeaterView.displayResponse("Invalid target: " + e.getMessage());
        } catch (IOException e) {
            repeaterView.displayResponse("Repeater send failed: " + e.getMessage());
        }
    }
}
