package it.abc.musical;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifica lo specifico comportamento di migrazione dati di V007 (non coperto da
 * AdminApiTest, che parte gia' con lo schema alla versione piu' recente e quindi non ha
 * mai righe pre-esistenti al momento in cui V007 gira): una riga che ha gia' un punto
 * focale desktop (hero_focus_x/y, V005) valorizzato PRIMA che V007 sia applicata deve
 * ritrovarsi con lo stesso punto copiato anche su hero_focus_mobile_x/y dopo la
 * migrazione, senza intervento applicativo.
 * <p>
 * Usa Flyway direttamente (non il context Spring, che migra sempre fino all'ultima
 * versione all'avvio) per poter fermarsi a V006, inserire dati, e poi migrare a V007.
 */
@Testcontainers
class HeroFocusMobileMigrationTest {

    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Test
    void copiesExistingDesktopFocusPointIntoMobileColumnsOnMigrate() throws Exception {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .target("6")
                .load()
                .migrate();

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    INSERT INTO shows (title, hero_focus_x, hero_focus_y)
                    VALUES ('Spettacolo con punto focale preesistente', 30, 70)
                    """);
            stmt.execute("""
                    INSERT INTO shows (title, hero_focus_x, hero_focus_y)
                    VALUES ('Spettacolo senza punto focale', NULL, NULL)
                    """);
            stmt.execute("""
                    INSERT INTO events (title, event_date, location_venue, hero_focus_x, hero_focus_y)
                    VALUES ('Evento con punto focale preesistente', CURRENT_TIMESTAMP, 'Teatro Prova', 45, 20)
                    """);
        }

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection conn = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("""
                    SELECT hero_focus_mobile_x, hero_focus_mobile_y FROM shows
                    WHERE title = 'Spettacolo con punto focale preesistente'
                    """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("hero_focus_mobile_x")).isEqualTo(30);
                assertThat(rs.getInt("hero_focus_mobile_y")).isEqualTo(70);
            }

            try (ResultSet rs = stmt.executeQuery("""
                    SELECT hero_focus_mobile_x, hero_focus_mobile_y FROM shows
                    WHERE title = 'Spettacolo senza punto focale'
                    """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getObject("hero_focus_mobile_x")).isNull();
                assertThat(rs.getObject("hero_focus_mobile_y")).isNull();
            }

            try (ResultSet rs = stmt.executeQuery("""
                    SELECT hero_focus_mobile_x, hero_focus_mobile_y FROM events
                    WHERE title = 'Evento con punto focale preesistente'
                    """)) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt("hero_focus_mobile_x")).isEqualTo(45);
                assertThat(rs.getInt("hero_focus_mobile_y")).isEqualTo(20);
            }
        }
    }
}
