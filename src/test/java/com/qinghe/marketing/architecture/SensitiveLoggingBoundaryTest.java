package com.qinghe.marketing.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SensitiveLoggingBoundaryTest {
    private static final Pattern LOG_CALL = Pattern.compile(
            "(?s)(?:LOGGER|logger|log)\\.(?:trace|debug|info|warn|error)\\s*\\((.*?)\\);");
    private static final Pattern STRING_LITERAL = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern SENSITIVE_ARGUMENT = Pattern.compile(
            ".*\\b(?:accesstoken|platformtoken|bearertoken|authorizationheader|authorization|"
                    + "rightcode|secret|signature|password|privatekey|phone|mobile)\\b.*");

    @Test
    void productionLogsMustNotReceiveRawSensitiveArguments() throws IOException {
        Path root = Paths.get(System.getProperty("user.dir"), "src", "main", "java",
                "com", "qinghe", "marketing");
        List<String> violations = new ArrayList<String>();
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> inspect(path, violations));
        }
        assertTrue(violations.isEmpty(), "sensitive values reached ordinary logging: " + violations);
    }

    private static void inspect(Path path, List<String> violations) {
        try {
            String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
            if (source.contains("System.out.print") || source.contains("System.err.print")) {
                violations.add(path + " uses a process console directly");
            }
            Matcher calls = LOG_CALL.matcher(source);
            while (calls.find()) {
                String arguments = STRING_LITERAL.matcher(calls.group(1)).replaceAll("")
                        .toLowerCase(Locale.ROOT).replace("_", "");
                if (SENSITIVE_ARGUMENT.matcher(arguments).matches()) {
                    violations.add(path + " passes a sensitive argument to a logger");
                }
            }
        } catch (IOException failure) {
            throw new IllegalStateException("cannot inspect logging source " + path, failure);
        }
    }
}
