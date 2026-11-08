-- Identidad logística autónoma. Se preservan todos los propietarios históricos.
CREATE TABLE clientes (
    id BINARY(16) NOT NULL,
    created_date DATETIME(6) NOT NULL,
    CONSTRAINT pk_clientes PRIMARY KEY (id)
);

INSERT INTO clientes(id, created_date)
SELECT cliente_id, MIN(created_date) FROM envios GROUP BY cliente_id;

CREATE INDEX idx_envios_cliente_estado ON envios(cliente_id, estado, id);
CREATE INDEX idx_envios_repartidor_estado ON envios(repartidor_id, estado, id);
ALTER TABLE envios ADD CONSTRAINT fk_envios_cliente_local
    FOREIGN KEY (cliente_id) REFERENCES clientes(id);
