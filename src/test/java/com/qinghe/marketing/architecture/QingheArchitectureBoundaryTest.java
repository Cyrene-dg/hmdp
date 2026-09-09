package com.qinghe.marketing.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;

class QingheArchitectureBoundaryTest {

    @Test
    void newModuleMustNotDependOnLegacyDomainLayers() throws IOException {
        Path root = Paths.get("src", "main", "java", "com", "qinghe", "marketing");
        List<String> violations = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                    if (source.matches("(?s).*import\\s+com\\.hmdp\\.(controller|service|entity|mapper)\\..*")) {
                        violations.add(path.toString());
                    }
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
        assertFalse(!violations.isEmpty(), "Qinghe module depends on legacy domain layers: " + violations);
    }
}
