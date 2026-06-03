package com.logger.appender;

import com.logger.core.LogEvent;
import com.logger.layout.Layout;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class FileAppender extends AbstractAppender {

    private final Path file;
    private BufferedWriter writer;

    public FileAppender(String name, Layout layout, Path file) {
        super(name, layout);
        this.file = file;
    }

    @Override
    public synchronized void start() {
        super.start();
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new RuntimeException("could not open file " + file, e);
        }
    }

    @Override
    protected synchronized void doAppend(LogEvent event, String formatted) {
        try {
            writer.write(formatted);
            // Cheap durability: flush on each event. In production: time/size buffer.
            writer.flush();
        } catch (IOException e) {
            System.err.println("[logger] file appender failed: " + e);
        }
    }

    @Override
    public synchronized void close() {
        super.close();
        try { if (writer != null) writer.close(); }
        catch (IOException ignored) {}
    }
}
