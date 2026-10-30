package javax.microedition.io;

import java.io.IOException;

public interface ContentConnection extends InputConnection {
    long getLength() throws IOException;
}
