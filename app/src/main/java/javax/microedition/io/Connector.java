package javax.microedition.io;

import java.io.IOException;

/**
 * The camera has no network (and this app deliberately holds no INTERNET
 * permission). JBenchmark's optional "Upload results" step wraps its
 * Connector.open call in try/catch (verified in the bytecode's exception
 * table), so failing here is handled gracefully by the original code and
 * shows the upload error text on the results form.
 */
public class Connector {

    public static Connection open(String name) throws IOException {
        throw new IOException("No network on this device");
    }
}
