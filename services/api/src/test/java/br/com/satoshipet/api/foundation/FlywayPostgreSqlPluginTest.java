package br.com.satoshipet.api.foundation;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** Garante que o plugin PostgreSQL do Flyway está disponível no classpath de testes. */
class FlywayPostgreSqlPluginTest {

    @Test
    void carregaConfiguracaoPostgreSqlSemExigirConexao() {
        assertDoesNotThrow(() -> Flyway.configure()
                .dataSource("jdbc:postgresql://localhost:5432/satoshi_pet_test", "satoshi", "satoshi-test-password")
                .locations("classpath:db/migration")
                .load());
    }
}
