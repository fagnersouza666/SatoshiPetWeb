package br.com.satoshipet.api.foundation;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercita as migrations e um restore de dump em PostgreSQL real. */
@Testcontainers(disabledWithoutDocker = true)
class FlywayPostgreSqlIT {

    private static final String POSTGRES_IMAGE = "postgres:18.6";
    private static final String SOURCE_DATABASE = "satoshi_pet_test";
    private static final String RESTORE_DATABASE = "satoshi_pet_restore";
    private static final String DUMP_FILE = "/tmp/satoshi-pet-foundation.dump";
    private static final String USERNAME = "satoshi";
    private static final String PASSWORD = "satoshi-test-password";
    private static final Instant FIXTURE_TIME = Instant.parse("2026-09-13T12:00:00Z");

    private static final UUID ACCOUNT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ADDRESS_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID BINDING_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID SESSION_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID OUTBOX_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID PET_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID MAGIC_LINK_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");
    private static final UUID TRANSACTION_ID = UUID.fromString("88888888-8888-4888-8888-888888888888");
    private static final UUID OUTPUT_ID = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final UUID SPEND_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID RECEIPT_ID = UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final UUID RECOVERY_CODE_ID = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc");

    private static final String ADDRESS = "bcrt1qrestorefixtureaddress000000000000000000000000";
    private static final String TRANSACTION_TXID = "0123456789abcdef".repeat(4);
    private static final String SPENDING_TXID = "fedcba9876543210".repeat(4);
    private static final String BLOCK_HASH = "abcdef0123456789".repeat(4);
    private static final Set<String> FOUNDATION_TABLES = Set.of(
            "accounts",
            "addresses",
            "account_address_bindings",
            "sessions",
            "outbox_events",
            "job_locks",
            "pets",
            "magic_link_tokens",
            "bitcoin_transactions",
            "bitcoin_outputs",
            "bitcoin_spends",
            "logical_receipts",
            "address_monitor_state",
            "recovery_codes"
    );

    @org.testcontainers.junit.jupiter.Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse(POSTGRES_IMAGE))
            .withDatabaseName(SOURCE_DATABASE)
            .withUsername(USERNAME)
            .withPassword(PASSWORD);

    @BeforeEach
    void preparaBancoFonteComSchemaVazio() throws Exception {
        try (Connection connection = connectionTo(SOURCE_DATABASE);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + RESTORE_DATABASE);
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }

        flywayFor(SOURCE_DATABASE).migrate();
    }

    @Test
    void aplicaTodasAsMigrationsPostgreSqlDesdeSchemaVazio() throws Exception {
        Flyway flyway = flywayFor(SOURCE_DATABASE);

        assertMigrationHistory(flyway);
        try (Connection connection = connectionTo(SOURCE_DATABASE)) {
            assertEquals(FOUNDATION_TABLES, tableNames(connection));
        }
    }

    @Test
    void restauraDumpEmBancoNovoEConservaDadosDaFundacao() throws Exception {
        try (Connection connection = connectionTo(SOURCE_DATABASE)) {
            insertFixture(connection);
        }

        assertCommandSucceeded(
                "pg_dump",
                POSTGRES.execInContainer(
                        "pg_dump",
                        "--format=custom",
                        "--no-owner",
                        "--no-privileges",
                        "--file=" + DUMP_FILE,
                        "--username=" + USERNAME,
                        "--dbname=" + SOURCE_DATABASE));

        try (Connection connection = connectionTo(SOURCE_DATABASE);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE " + RESTORE_DATABASE);
        }

        assertCommandSucceeded(
                "pg_restore",
                POSTGRES.execInContainer(
                        "pg_restore",
                        "--exit-on-error",
                        "--clean",
                        "--if-exists",
                        "--no-owner",
                        "--no-privileges",
                        "--username=" + USERNAME,
                        "--dbname=" + RESTORE_DATABASE,
                        DUMP_FILE));

        Flyway restoredFlyway = flywayFor(RESTORE_DATABASE);
        restoredFlyway.validate();
        assertMigrationHistory(restoredFlyway);

        try (Connection connection = connectionTo(RESTORE_DATABASE)) {
            assertEquals(FOUNDATION_TABLES, tableNames(connection));
            assertFixtureRestored(connection);
        }
    }

    private static Flyway flywayFor(String database) {
        return Flyway.configure()
                .dataSource(jdbcUrl(database), USERNAME, PASSWORD)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .cleanDisabled(false)
                .load();
    }

    private static void assertMigrationHistory(Flyway flyway) {
        MigrationInfo[] migrations = flyway.info().all();

        assertEquals(4, migrations.length, "O catálogo deve conter V1 a V4");
        assertTrue(Arrays.stream(migrations)
                .allMatch(migration -> migration.getState() == MigrationState.SUCCESS),
                "Todas as migrations devem estar aplicadas com sucesso");
        assertEquals("4", flyway.info().current().getVersion().getVersion());
    }

    private static Set<String> tableNames(Connection connection) throws SQLException {
        Set<String> tables = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'")) {
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    tables.add(result.getString(1));
                }
            }
        }
        return tables;
    }

    private static void insertFixture(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO accounts (id, email, created_at, timezone, locale, address_change_deadline) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, ACCOUNT_ID);
            statement.setString(2, "restore@example.test");
            setInstant(statement, 3, FIXTURE_TIME);
            statement.setString(4, "America/Sao_Paulo");
            statement.setString(5, "pt-BR");
            setInstant(statement, 6, FIXTURE_TIME.plusSeconds(72 * 60 * 60));
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO addresses (id, canonical, created_at) VALUES (?, ?, ?)")) {
            statement.setObject(1, ADDRESS_ID);
            statement.setString(2, ADDRESS);
            setInstant(statement, 3, FIXTURE_TIME);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO account_address_bindings "
                        + "(id, account_id, address_id, bound_at, is_primary, unbound_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, BINDING_ID);
            statement.setObject(2, ACCOUNT_ID);
            statement.setObject(3, ADDRESS_ID);
            setInstant(statement, 4, FIXTURE_TIME);
            statement.setBoolean(5, true);
            statement.setTimestamp(6, null);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sessions "
                        + "(id, account_id, created_at, expires_at, invalidated_at, user_agent, ip_address, "
                        + "token_hash, csrf_token_hash) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, SESSION_ID);
            statement.setObject(2, ACCOUNT_ID);
            setInstant(statement, 3, FIXTURE_TIME);
            setInstant(statement, 4, FIXTURE_TIME.plusSeconds(3600));
            statement.setTimestamp(5, null);
            statement.setString(6, "test-agent");
            statement.setString(7, "192.0.2.10");
            statement.setString(8, "1".repeat(64));
            statement.setString(9, "2".repeat(64));
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO outbox_events "
                        + "(id, aggregate_type, aggregate_id, event_type, payload, created_at, processed_at, "
                        + "correlation_id, retries) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, OUTBOX_ID);
            statement.setString(2, "Account");
            statement.setString(3, ACCOUNT_ID.toString());
            statement.setString(4, "BITCOIN_TRANSACTION_OBSERVED");
            statement.setString(5, "{\"address\":\"" + ADDRESS + "\"}");
            setInstant(statement, 6, FIXTURE_TIME);
            statement.setTimestamp(7, null);
            statement.setString(8, "restore-correlation");
            statement.setInt(9, 0);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO job_locks (job_name, owner_id, acquired_at, expires_at) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, "bitcoin-monitor");
            statement.setString(2, "restore-worker");
            setInstant(statement, 3, FIXTURE_TIME);
            setInstant(statement, 4, FIXTURE_TIME.plusSeconds(900));
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO pets (id, address_id, creator_account_id, name, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, PET_ID);
            statement.setObject(2, ADDRESS_ID);
            statement.setObject(3, ACCOUNT_ID);
            statement.setString(4, "Pet restore");
            setInstant(statement, 5, FIXTURE_TIME);
            setInstant(statement, 6, FIXTURE_TIME);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO magic_link_tokens "
                        + "(id, email, token_hash, issued_at, expires_at, consumed_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, MAGIC_LINK_ID);
            statement.setString(2, "restore@example.test");
            statement.setString(3, "3".repeat(64));
            setInstant(statement, 4, FIXTURE_TIME);
            setInstant(statement, 5, FIXTURE_TIME.plusSeconds(900));
            statement.setTimestamp(6, null);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bitcoin_transactions "
                        + "(id, txid, address_id, status, amount_sats, observed_at, confirmed_at, block_height, block_hash) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, TRANSACTION_ID);
            statement.setString(2, TRANSACTION_TXID);
            statement.setObject(3, ADDRESS_ID);
            statement.setString(4, "CONFIRMED");
            statement.setLong(5, 125_000L);
            setInstant(statement, 6, FIXTURE_TIME);
            setInstant(statement, 7, FIXTURE_TIME.plusSeconds(600));
            statement.setInt(8, 100);
            statement.setString(9, BLOCK_HASH);
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bitcoin_outputs "
                        + "(id, transaction_id, output_index, address_id, value_sats, script) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, OUTPUT_ID);
            statement.setObject(2, TRANSACTION_ID);
            statement.setInt(3, 0);
            statement.setObject(4, ADDRESS_ID);
            statement.setLong(5, 125_000L);
            statement.setString(6, "fixture-script");
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO bitcoin_spends (id, output_id, spending_txid, observed_at) VALUES (?, ?, ?, ?)")) {
            statement.setObject(1, SPEND_ID);
            statement.setObject(2, OUTPUT_ID);
            statement.setString(3, SPENDING_TXID);
            setInstant(statement, 4, FIXTURE_TIME.plusSeconds(1200));
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO logical_receipts "
                        + "(id, address_id, reference_txid, amount_sats, confirmed_sats, pending_sats, created_at, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setObject(1, RECEIPT_ID);
            statement.setObject(2, ADDRESS_ID);
            statement.setString(3, TRANSACTION_TXID);
            statement.setLong(4, 125_000L);
            statement.setLong(5, 125_000L);
            statement.setLong(6, 0L);
            setInstant(statement, 7, FIXTURE_TIME);
            setInstant(statement, 8, FIXTURE_TIME.plusSeconds(600));
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO address_monitor_state (address_id, last_seen_txid, last_checked_at, cursor) "
                        + "VALUES (?, ?, ?, ?)")) {
            statement.setObject(1, ADDRESS_ID);
            statement.setString(2, TRANSACTION_TXID);
            setInstant(statement, 3, FIXTURE_TIME.plusSeconds(1200));
            statement.setString(4, "restore-cursor");
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO recovery_codes (id, account_id, code_hash, created_at, used_at) VALUES (?, ?, ?, ?, ?)")) {
            statement.setObject(1, RECOVERY_CODE_ID);
            statement.setObject(2, ACCOUNT_ID);
            statement.setString(3, "4".repeat(64));
            setInstant(statement, 4, FIXTURE_TIME);
            statement.setTimestamp(5, null);
            statement.executeUpdate();
        }
    }

    private static void assertFixtureRestored(Connection connection) throws SQLException {
        assertEquals(1L, count(connection, "accounts"));
        assertEquals(1L, count(connection, "addresses"));
        assertEquals(1L, count(connection, "account_address_bindings"));
        assertEquals(1L, count(connection, "sessions"));
        assertEquals(1L, count(connection, "outbox_events"));
        assertEquals(1L, count(connection, "job_locks"));
        assertEquals(1L, count(connection, "pets"));
        assertEquals(1L, count(connection, "magic_link_tokens"));
        assertEquals(1L, count(connection, "bitcoin_transactions"));
        assertEquals(1L, count(connection, "bitcoin_outputs"));
        assertEquals(1L, count(connection, "bitcoin_spends"));
        assertEquals(1L, count(connection, "logical_receipts"));
        assertEquals(1L, count(connection, "address_monitor_state"));
        assertEquals(1L, count(connection, "recovery_codes"));
        assertEquals("restore@example.test", value(connection,
                "SELECT email FROM accounts WHERE id = '11111111-1111-4111-8111-111111111111'"));
        assertEquals("Pet restore", value(connection,
                "SELECT name FROM pets WHERE id = '66666666-6666-4666-8666-666666666666'"));
        assertEquals(125_000L, countValue(connection,
                "SELECT confirmed_sats FROM logical_receipts WHERE id = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'"));
        assertEquals("restore-cursor", value(connection,
                "SELECT cursor FROM address_monitor_state WHERE address_id = '22222222-2222-4222-8222-222222222222'"));
    }

    private static long count(Connection connection, String table) throws SQLException {
        return countValue(connection, "SELECT COUNT(*) FROM " + table);
    }

    private static long countValue(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private static String value(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static void setInstant(PreparedStatement statement, int index, Instant value) throws SQLException {
        statement.setTimestamp(index, Timestamp.from(value));
    }

    private static void assertCommandSucceeded(String command, Container.ExecResult result) {
        assertEquals(0, result.getExitCode(), () -> command + " falhou: " + result.getStderr());
    }

    private static Connection connectionTo(String database) throws SQLException {
        return DriverManager.getConnection(jdbcUrl(database), USERNAME, PASSWORD);
    }

    private static String jdbcUrl(String database) {
        return POSTGRES.getJdbcUrl().replace("/" + SOURCE_DATABASE, "/" + database);
    }
}
