-- ═══════════════════════════════════════════════════════════════════
-- V1 — Esquema de fleets-drivers en PostgreSQL (esquema fleets_drivers)
-- El estado vivo de conductores y vehículos está en Redis; aquí solo el outbox de eventos.
-- Idempotente: en una base existente la tabla ya la movió db-init desde public (creada por Hibernate).
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS fleets_drivers_outbox_events (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(100)   NOT NULL,
    aggregate_id   VARCHAR(100)   NOT NULL,
    event_type     VARCHAR(150)   NOT NULL,
    payload        TEXT           NOT NULL,
    occurred_at    TIMESTAMPTZ(6) NOT NULL,
    created_at     TIMESTAMPTZ(6) NOT NULL,
    published_at   TIMESTAMPTZ(6),
    attempts       INTEGER        NOT NULL,
    last_error     VARCHAR(2000)
);

CREATE INDEX IF NOT EXISTS idx_fleet_outbox_unpublished ON fleets_drivers_outbox_events (published_at, created_at);
