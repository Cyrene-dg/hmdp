package com.qinghe.marketing.reconciliation;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

@Component
public class LocalReconciliationFileGateway {
    private static final String MANIFEST_SUFFIX = ".manifest.json";
    private final Path baseDirectory;

    public LocalReconciliationFileGateway(
            @Value("${qinghe.reconciliation.file.base-directory:}") String configuredBase) {
        this.baseDirectory = configuredBase == null || configuredBase.trim().isEmpty()
                ? null : safeBase(Paths.get(configuredBase));
    }

    public boolean isConfigured() {
        return baseDirectory != null;
    }

    public List<Path> discoverManifests() throws IOException {
        Path inbound = directory("inbound");
        Files.createDirectories(inbound);
        List<Path> manifests = new ArrayList<Path>();
        try (Stream<Path> entries = Files.list(inbound)) {
            entries.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(MANIFEST_SUFFIX))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .forEach(manifests::add);
        }
        return manifests;
    }

    public Path pairedCsv(Path manifest) {
        verifyUnderBase(manifest);
        String name = manifest.getFileName().toString();
        if (!name.endsWith(MANIFEST_SUFFIX)) {
            throw new IllegalArgumentException("reconciliation manifest name is invalid");
        }
        return manifest.resolveSibling(name.substring(0,
                name.length() - MANIFEST_SUFFIX.length()) + ".csv");
    }

    public ReconciliationStagedFile stage(Path manifest) throws IOException {
        Path csv = pairedCsv(manifest);
        if (!Files.isRegularFile(manifest) || !Files.isRegularFile(csv)) {
            throw new IOException("reconciliation file pair is incomplete");
        }
        Path processing = directory("processing");
        Files.createDirectories(processing);
        String stem = manifest.getFileName().toString()
                .substring(0, manifest.getFileName().toString().length() - MANIFEST_SUFFIX.length());
        Path staging = processing.resolve(stem + "-" + UUID.randomUUID()).normalize();
        verifyUnderBase(staging);
        Files.createDirectory(staging);
        Path stagedManifest = staging.resolve(manifest.getFileName());
        Path stagedCsv = staging.resolve(csv.getFileName());
        move(manifest, stagedManifest);
        try {
            move(csv, stagedCsv);
        } catch (IOException failure) {
            move(stagedManifest, manifest);
            Files.deleteIfExists(staging);
            throw failure;
        }
        return new ReconciliationStagedFile(staging, stagedManifest, stagedCsv);
    }

    public void archive(ReconciliationStagedFile staged, String bucket) throws IOException {
        if (!bucket.matches("success|conflict|error")) {
            throw new IllegalArgumentException("reconciliation archive bucket is invalid");
        }
        verifyUnderBase(staged.directory());
        Path archiveRoot = directory("archive").resolve(bucket).normalize();
        verifyUnderBase(archiveRoot);
        Files.createDirectories(archiveRoot);
        Path target = archiveRoot.resolve(staged.directory().getFileName()).normalize();
        verifyUnderBase(target);
        move(staged.directory(), target);
        staged = new ReconciliationStagedFile(target,
                target.resolve(staged.manifest().getFileName()),
                target.resolve(staged.csv().getFileName()));
        staged.manifest().toFile().setReadOnly();
        staged.csv().toFile().setReadOnly();
    }

    public ReconciliationStagedFile findFailed(String fileName) throws IOException {
        if (fileName == null || !fileName.matches("POS_[A-Za-z0-9._:-]+\\.csv")) {
            return null;
        }
        Path error = directory("archive").resolve("error").normalize();
        if (!Files.isDirectory(error)) return null;
        try (Stream<Path> paths = Files.walk(error, 3)) {
            Path csv = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .findFirst().orElse(null);
            if (csv == null) return null;
            String manifestName = fileName.substring(0, fileName.length() - 4) + MANIFEST_SUFFIX;
            Path manifest = csv.resolveSibling(manifestName);
            return Files.isRegularFile(manifest)
                    ? new ReconciliationStagedFile(csv.getParent(), manifest, csv) : null;
        }
    }

    public byte[] read(Path file) throws IOException {
        verifyUnderBase(file);
        return Files.readAllBytes(file);
    }

    private Path directory(String name) {
        if (baseDirectory == null) {
            throw new IllegalStateException("reconciliation base directory is not configured");
        }
        Path path = baseDirectory.resolve(name).normalize();
        verifyUnderBase(path);
        return path;
    }

    private void verifyUnderBase(Path path) {
        if (baseDirectory == null || path == null
                || !path.toAbsolutePath().normalize().startsWith(baseDirectory)) {
            throw new IllegalArgumentException("reconciliation path escapes configured base");
        }
    }

    private static Path safeBase(Path candidate) {
        Path base = candidate.toAbsolutePath().normalize();
        if (base.getParent() == null) {
            throw new IllegalArgumentException("reconciliation base directory cannot be a root");
        }
        return base;
    }

    private static void move(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
    }
}
