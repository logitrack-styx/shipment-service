package com.logitrack.shipment.repository;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.mysql.MySQLContainer;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Migra datos previos a Cliente y comprueba backfill, FK e índices sobre MySQL real.
 * @author dominox45
 */
@Testcontainers
class ClienteMigrationTest {
    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4");

    @Test
    void backfillsHistoricalOwnersAndBoundsTenThousandRows() throws Exception {
        var config = Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        config.target("3").load().migrate();
        try (var connection = DriverManager.getConnection(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("""
                INSERT INTO envios(id,created_date,last_modified_date,codigo_seguimiento,peso,ancho,alto,largo,estado,
                origen_calle,origen_numero,origen_ciudad,origen_codigo_postal,
                destino_calle,destino_numero,destino_ciudad,destino_codigo_postal,cliente_id,version)
                VALUES(UNHEX(MD5('legacy')),NOW(6),NOW(6),UNHEX(MD5('tracking')),1,1,1,1,'PENDIENTE',
                'Calle','1','Ciudad','1000','Calle','2','Ciudad','1000',UNHEX(MD5('owner')),0)
                """);
            Flyway.configure().dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword()).load().migrate();
            try (var rs = statement.executeQuery("SELECT COUNT(*) FROM clientes")) { rs.next(); assertEquals(1, rs.getInt(1)); }
            try (var rs = statement.executeQuery("SELECT COUNT(*) FROM envios e LEFT JOIN clientes c ON e.cliente_id=c.id WHERE c.id IS NULL")) {
                rs.next(); assertEquals(0, rs.getInt(1));
            }
            statement.execute("SET SESSION cte_max_recursion_depth=10001");
            long started = System.nanoTime();
            statement.execute("""
                INSERT INTO envios(id,created_date,last_modified_date,codigo_seguimiento,peso,ancho,alto,largo,estado,
                origen_calle,origen_numero,origen_ciudad,origen_codigo_postal,
                destino_calle,destino_numero,destino_ciudad,destino_codigo_postal,cliente_id,version)
                WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i+1 FROM n WHERE i<10000)
                SELECT UNHEX(MD5(CONCAT('envio',i))),NOW(6),NOW(6),UNHEX(MD5(CONCAT('track',i))),1,1,1,1,'PENDIENTE',
                'Calle','1','Ciudad','1000','Calle','2','Ciudad','1000',UNHEX(MD5('owner')),0 FROM n
                """);
            try (var rs = statement.executeQuery("SELECT id FROM envios WHERE cliente_id=UNHEX(MD5('owner')) AND estado='PENDIENTE' ORDER BY id LIMIT 100 OFFSET 100")) {
                int rows = 0; while (rs.next()) rows++;
                assertEquals(100, rows);
            }
            System.out.println("Scale fixture: 10001 rows; bounded page 100; insertion+query ms=" + (System.nanoTime()-started)/1_000_000);
            assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate("UPDATE envios SET cliente_id=UNHEX(MD5('missing')) WHERE id=UNHEX(MD5('legacy'))"));
            try (var rs = statement.executeQuery("SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND INDEX_NAME='idx_envios_cliente_estado'")) {
                rs.next(); assertEquals(3, rs.getInt(1));
            }
        }
    }
}
