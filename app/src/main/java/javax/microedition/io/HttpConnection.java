package javax.microedition.io;

import java.io.IOException;

public interface HttpConnection extends ContentConnection {
    String HTTP_GET = "GET";
    String HTTP_POST = "POST";

    void setRequestMethod(String method) throws IOException;

    void setRequestProperty(String name, String value) throws IOException;

    int getResponseCode() throws IOException;
}
