package javax.microedition.io;

import java.io.IOException;

/** Minimal GCF Connection. */
public interface Connection {
    void close() throws IOException;
}
