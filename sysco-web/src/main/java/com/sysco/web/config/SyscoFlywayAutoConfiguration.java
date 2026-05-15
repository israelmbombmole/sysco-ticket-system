package com.sysco.web.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceUtils;

/**
 * Runs <strong>before</strong> {@link FlywayAutoConfiguration} so this {@link FlywayMigrationStrategy} replaces the
 * default and H2 repair runs prior to {@link Flyway#migrate()}. (A {@code @Configuration} with
 * {@code @ConditionalOnBean(Flyway.class)} is evaluated too early and is skipped, so Flyway never sees the strategy.)
 *
 * <p>Recovers local H2 databases where SYSCO tables exist without a successful Flyway row, or where
 * {@code flyway_schema_history} contains a failed migration (e.g. after a previous "table already exists" error).
 */
@AutoConfiguration(before = FlywayAutoConfiguration.class)
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(prefix = "spring.flyway", name = "enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class SyscoFlywayAutoConfiguration {

    @Bean
    @Primary
    FlywayMigrationStrategy syscoFlywayMigrationStrategy(
            DataSource dataSource, @Value("${sysco.flyway.repair-orphan-h2:true}") boolean repairOrphanH2) {
        return flyway -> {
            if (repairOrphanH2) {
                maybeRepairOrphanH2(dataSource);
            }
            flyway.migrate();
        };
    }

    private void maybeRepairOrphanH2(DataSource dataSource) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        boolean resetAutoCommit = false;
        boolean oldAutoCommit = true;
        try {
            String url = connection.getMetaData().getURL();
            if (url == null || !url.toLowerCase().contains("jdbc:h2:")) {
                return;
            }
            long successful = countSuccessfulFlywayMigrations(connection);
            if (successful > 0) {
                return;
            }
            boolean failedMigration = hasFailedFlywayMigration(connection);
            boolean orphanTables = knownSyscoTablePresent(connection);
            if (!failedMigration && !orphanTables) {
                return;
            }
            log.warn(
                    "H2 Flyway repair: {} - resetting schema so migrations can run "
                            + "(disable with sysco.flyway.repair-orphan-h2=false).",
                    failedMigration
                            ? "failed migration row(s) in flyway_schema_history (e.g. interrupted V1)"
                            : "SYSCO tables present but no successful Flyway migration");
            /*
             * Pool connections may have autoCommit=false; DDL would roll back on release and Flyway would still see
             * the failed migration / orphan tables.
             */
            oldAutoCommit = connection.getAutoCommit();
            if (!oldAutoCommit) {
                connection.setAutoCommit(true);
                resetAutoCommit = true;
            }
            try (Statement st = connection.createStatement()) {
                st.execute("DROP ALL OBJECTS");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not inspect or repair H2 schema before Flyway migrate", e);
        } finally {
            if (resetAutoCommit) {
                try {
                    connection.setAutoCommit(oldAutoCommit);
                } catch (SQLException ignored) {
                    // best-effort restore
                }
            }
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    /**
     * H2 stores Flyway's table as quoted {@code "flyway_schema_history"}; unquoted {@code flyway_schema_history}
     * resolves to {@code FLYWAY_SCHEMA_HISTORY} and misses the real table. Exclude {@code installed_rank <= 0}
     * (bootstrap row) so a failed V1 with only the meta success row still triggers repair.
     */
    private static long countSuccessfulFlywayMigrations(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs =
                        st.executeQuery(
                                "SELECT COUNT(*) FROM \"flyway_schema_history\" "
                                        + "WHERE success = TRUE AND installed_rank > 0")) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            /* Table missing or not readable — treat as no successful migrations. */
            return 0;
        }
    }

    private static boolean hasFailedFlywayMigration(Connection c) throws SQLException {
        try (Statement st = c.createStatement();
                ResultSet rs =
                        st.executeQuery(
                                "SELECT COUNT(*) FROM \"flyway_schema_history\" "
                                        + "WHERE success IS NOT TRUE AND installed_rank > 0")) {
            return rs.next() && rs.getLong(1) > 0;
        } catch (SQLException e) {
            return false;
        }
    }

    /** True if the first SYSCO table from V1 exists (direct probe; empty table still counts). */
    private static boolean knownSyscoTablePresent(Connection c) {
        try (Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM sous_directions")) {
            return rs.next();
        } catch (SQLException e) {
            return false;
        }
    }
}
