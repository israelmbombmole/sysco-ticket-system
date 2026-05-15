package com.sysco.web;

import com.sysco.web.config.SyscoFlywayAutoConfiguration;
import io.github.cdimascio.dotenv.Dotenv;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ImportAutoConfiguration(SyscoFlywayAutoConfiguration.class)
public class SyscoWebApplication {

    public static void main(String[] args) {
        loadDotEnvIntoSystemPropertiesIfAbsent();
        SpringApplication.run(SyscoWebApplication.class, args);
    }

    /**
     * Makes {@code SYSCO_OPENAI_API_KEY} (and other vars) easy to set via a local {@code .env} file.
     * Never overrides real OS environment variables or JVM {@code -D} properties.
     */
    static void loadDotEnvIntoSystemPropertiesIfAbsent() {
        Path cwd = Path.of("").toAbsolutePath().normalize();
        List<Path> candidates = new ArrayList<>();
        candidates.add(cwd.resolve(".env"));
        if (!"sysco-web".equalsIgnoreCase(String.valueOf(cwd.getFileName()))) {
            candidates.add(cwd.resolve("sysco-web").resolve(".env"));
        }

        for (Path envFile : candidates) {
            if (!Files.isRegularFile(envFile)) {
                continue;
            }
            try {
                Dotenv dotenv =
                        Dotenv.configure()
                                .directory(envFile.getParent().toString())
                                .filename(envFile.getFileName().toString())
                                .ignoreIfMalformed()
                                .load();
                dotenv.entries()
                        .forEach(
                                e -> {
                                    String k = e.getKey();
                                    if (k == null || k.isBlank()) {
                                        return;
                                    }
                                    if (System.getenv(k) != null) {
                                        return;
                                    }
                                    String prop = System.getProperty(k);
                                    if (prop != null && !prop.isEmpty()) {
                                        return;
                                    }
                                    System.setProperty(k, e.getValue());
                                });
                return;
            } catch (Exception ignored) {
                // try next path
            }
        }
    }
}
