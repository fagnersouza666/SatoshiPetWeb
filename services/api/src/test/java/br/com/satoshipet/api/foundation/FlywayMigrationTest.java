package br.com.satoshipet.api.foundation;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica que o startup aplica o catálogo completo em um schema inicialmente vazio. */
@QuarkusTest
class FlywayMigrationTest {

    private static final Set<String> FOUNDATION_TABLES = Set.of(
            "ACCOUNTS",
            "ADDRESSES",
            "ACCOUNT_ADDRESS_BINDINGS",
            "OUTBOX_EVENTS",
            "JOB_LOCKS",
            "PETS",
            "MAGIC_LINK_TOKENS",
            "BITCOIN_TRANSACTIONS",
            "BITCOIN_OUTPUTS",
            "BITCOIN_SPENDS",
            "LOGICAL_RECEIPTS",
            "ADDRESS_MONITOR_STATE",
            "RECOVERY_CODES"
    );

    @Inject
    Flyway flyway;

    @Inject
    DataSource dataSource;

    @Test
    void aplicaTodasAsMigrationsERegistraSucesso() {
        MigrationInfo[] migrations = flyway.info().all();

        assertTrue(migrations.length > 0, "O catálogo Flyway deve conter migrations");
        assertTrue(Arrays.stream(migrations)
                .allMatch(migration -> migration.getState() == MigrationState.SUCCESS),
                "Todas as migrations devem estar aplicadas com sucesso");

        MigrationInfo current = flyway.info().current();
        assertNotNull(current, "O banco deve ter uma versão Flyway corrente");
        assertEquals(migrations[migrations.length - 1].getVersion(), current.getVersion());
    }

    @Test
    void criaTabelasDaFundacaoNoSchemaInicialmenteVazio() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                             + "WHERE TABLE_SCHEMA = 'PUBLIC' AND TABLE_NAME = ?")) {
            for (String table : FOUNDATION_TABLES) {
                statement.setString(1, table);
                try (ResultSet result = statement.executeQuery()) {
                    assertTrue(result.next());
                    assertEquals(1, result.getInt(1), "Tabela ausente: " + table);
                }
            }
        }
    }
}
