package ru.hgd.sdlc.runtime.infrastructure.fs;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import ru.hgd.sdlc.runtime.application.port.WorkspacePort;

@Component
public class DefaultWorkspaceAdapter implements WorkspacePort {
    @Override
    public boolean exists(Path path) {
        return path != null && Files.exists(path);
    }

    @Override
    public boolean isDirectory(Path path) {
        return path != null && Files.isDirectory(path);
    }

    @Override
    public void createDirectories(Path path) throws IOException {
        if (path != null) {
            Files.createDirectories(path);
        }
    }

    @Override
    public void write(Path path, byte[] content) throws IOException {
        Files.write(path, content);
    }

    @Override
    public void writeString(Path path, String content, Charset charset, OpenOption... options) throws IOException {
        Files.writeString(path, content, charset, options);
    }

    @Override
    public String readString(Path path, Charset charset) throws IOException {
        return Files.readString(path, charset);
    }

    @Override
    public byte[] readAllBytes(Path path) throws IOException {
        return Files.readAllBytes(path);
    }

    @Override
    public long size(Path path) throws IOException {
        return Files.size(path);
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        Files.copy(source, target, options);
    }

    @Override
    public void deleteIfExists(Path path) throws IOException {
        Files.deleteIfExists(path);
    }

    @Override
    public List<Path> listRegularFilesRecursively(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).toList();
        }
    }

    @Override
    public List<Path> listDescendantsReverse(Path root) throws IOException {
        try (var stream = Files.walk(root)) {
            return stream.sorted(Comparator.reverseOrder()).filter((path) -> !path.equals(root)).toList();
        }
    }

    @Override
    public ReadChunkResult readChunk(Path path, long offset, int maxBytes) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "r")) {
            long fileLength = raf.length();
            long normalizedOffset = Math.max(offset, 0L);
            if (normalizedOffset >= fileLength) {
                return new ReadChunkResult(normalizedOffset, fileLength, new byte[0], 0);
            }
            raf.seek(normalizedOffset);
            int chunkSize = (int) Math.min(fileLength - normalizedOffset, Math.max(maxBytes, 0));
            byte[] buffer = new byte[chunkSize];
            int read = raf.read(buffer);
            if (read <= 0) {
                return new ReadChunkResult(normalizedOffset, fileLength, new byte[0], 0);
            }
            return new ReadChunkResult(normalizedOffset, fileLength, buffer, read);
        }
    }
}
