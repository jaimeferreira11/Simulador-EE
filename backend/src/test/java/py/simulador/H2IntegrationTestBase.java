package py.simulador;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import py.simulador.config.H2JdbcConversions;

/**
 * Base for tests that run against an H2 in-memory database (no Docker required).
 *
 * <p><strong>Scope:</strong> intentionally narrow. The MVP migrations rely on
 * Postgres-only features ({@code jsonb}, {@code pgcrypto}, large {@code plpgsql}
 * {@code DO} blocks for seeding) that H2 cannot reproduce. To keep H2 viable we
 * skip Flyway entirely and load a tiny hand-written schema from
 * {@code src/test/resources/h2/schema.sql} (plus optional {@code data.sql}).
 *
 * <p><strong>Use this base for:</strong> repository-level tests over simple
 * tables (currently only {@code entidad}). Add a table to {@code h2/schema.sql}
 * before adding a test for it.
 *
 * <p><strong>Do NOT use for:</strong> tests that exercise auth (BCrypt-hashed
 * users live in the Postgres seed), the simulation engine, JSONB columns, or
 * any controller that requires the full domain. Use {@link IntegrationTestBase}
 * (Testcontainers) for those.
 *
 * <p>See {@code backend/src/test/H2_TESTS_README.md}.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = H2IntegrationTestBase.H2TestApplication.class
)
@ActiveProfiles("test-h2")
public abstract class H2IntegrationTestBase {

    /**
     * Trimmed-down Spring Boot configuration: scans only the entidad package
     * (the only one ported so far) and excludes web/security/websocket/llm
     * autoconfig. {@link H2JdbcConversions} is imported explicitly because the
     * default {@code JdbcConversions} bean is disabled under this profile.
     *
     * <p>Add another package to {@code basePackages} if you port more tests.
     * Be aware that scanning new packages may pull in beans that depend on
     * websocket/security/etc.
     */
    @EnableAutoConfiguration(exclude = {
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    })
    @ComponentScan(
            basePackages = {
                    "py.simulador.common",
                    "py.simulador.entidad"
            },
            excludeFilters = @ComponentScan.Filter(
                    type = FilterType.REGEX,
                    pattern = ".*Controller$"
            )
    )
    @Import(H2JdbcConversions.class)
    public static class H2TestApplication {
    }
}
