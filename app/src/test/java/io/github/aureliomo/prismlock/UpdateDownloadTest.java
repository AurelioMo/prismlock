package io.github.aureliomo.prismlock;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class UpdateDownloadTest {
    @Rule public final TemporaryFolder directory = new TemporaryFolder();

    @Test public void interruptedTransferRemovesPartialFileAndPreservesExistingDownload() throws Exception {
        File target = directory.newFile("update.apk");
        Files.write(target.toPath(), new byte[]{42});

        byte[] content = {1, 2, 3};
        HttpURLConnection connection = new HttpURLConnection(new URL("https://example.invalid/update")) {
            @Override public void connect() {}
            @Override public void disconnect() {}
            @Override public boolean usingProxy() { return false; }
            @Override public int getResponseCode() { return HTTP_OK; }
            @Override public long getContentLengthLong() { return content.length; }
            @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        };
        UpdateChecker.Release release = new UpdateChecker.Release("v99.0.0", "99.0.0", "", "",
                connection.getURL().toString(), "", content.length);
        assertThrows(IllegalStateException.class, () -> UpdateDownload.transfer(connection, release, percent -> {
            throw new IllegalStateException("Interrupted during progress reporting");
        }, target));

        assertFalse(new File(directory.getRoot(), "update.apk.part").exists());
        assertArrayEquals(new byte[]{42}, Files.readAllBytes(target.toPath()));
    }
}
