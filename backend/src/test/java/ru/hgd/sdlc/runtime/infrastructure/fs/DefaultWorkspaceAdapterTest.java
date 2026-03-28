package ru.hgd.sdlc.runtime.infrastructure.fs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.hgd.sdlc.runtime.application.port.WorkspacePort;

class DefaultWorkspaceAdapterTest {

    @TempDir
    Path tempDir;

    @Test
    void supportsWorkspaceFileOperations() throws Exception {
        DefaultWorkspaceAdapter adapter = new DefaultWorkspaceAdapter();
        Path nested = tempDir.resolve("a/b");
        Path source = nested.resolve("source.txt");
        Path copy = tempDir.resolve("copy.txt");

        adapter.createDirectories(nested);
        adapter.write(source, "hello".getBytes(StandardCharsets.UTF_8));
        adapter.copy(source, copy, StandardCopyOption.REPLACE_EXISTING);

        Assertions.assertTrue(adapter.exists(source));
        Assertions.assertFalse(adapter.isDirectory(source));
        Assertions.assertEquals("hello", adapter.readString(source, StandardCharsets.UTF_8));
        Assertions.assertEquals(5L, adapter.size(source));
        Assertions.assertTrue(adapter.listRegularFilesRecursively(tempDir).contains(source));
        Assertions.assertTrue(adapter.listRegularFilesRecursively(tempDir).contains(copy));
        Assertions.assertFalse(adapter.listDescendantsReverse(tempDir).isEmpty());

        WorkspacePort.ReadChunkResult chunk = adapter.readChunk(source, 1L, 3);
        Assertions.assertEquals(1L, chunk.normalizedOffset());
        Assertions.assertEquals(5L, chunk.fileLength());
        Assertions.assertEquals("ell", new String(chunk.data(), 0, chunk.bytesRead(), StandardCharsets.UTF_8));
    }

    @Test
    void readStringFailsForMissingFile() {
        DefaultWorkspaceAdapter adapter = new DefaultWorkspaceAdapter();
        Path missing = tempDir.resolve("missing.txt");
        Assertions.assertThrows(IOException.class, () -> adapter.readString(missing, StandardCharsets.UTF_8));
    }
}

