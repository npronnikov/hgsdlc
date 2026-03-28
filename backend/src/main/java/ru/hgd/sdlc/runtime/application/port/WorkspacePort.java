package ru.hgd.sdlc.runtime.application.port;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.CopyOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.List;

public interface WorkspacePort {
    boolean exists(Path path);

    boolean isDirectory(Path path);

    void createDirectories(Path path) throws IOException;

    void write(Path path, byte[] content) throws IOException;

    void writeString(Path path, String content, Charset charset, OpenOption... options) throws IOException;

    String readString(Path path, Charset charset) throws IOException;

    byte[] readAllBytes(Path path) throws IOException;

    long size(Path path) throws IOException;

    void copy(Path source, Path target, CopyOption... options) throws IOException;

    void deleteIfExists(Path path) throws IOException;

    List<Path> listRegularFilesRecursively(Path root) throws IOException;

    List<Path> listDescendantsReverse(Path root) throws IOException;

    ReadChunkResult readChunk(Path path, long offset, int maxBytes) throws IOException;

    record ReadChunkResult(
            long normalizedOffset,
            long fileLength,
            byte[] data,
            int bytesRead
    ) {}
}
