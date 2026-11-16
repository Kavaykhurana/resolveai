CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    name          VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'AGENT')),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE orders (
    id           BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(20)   NOT NULL UNIQUE,
    customer_id  BIGINT        NOT NULL REFERENCES users (id),
    items        JSONB         NOT NULL,
    status       VARCHAR(20)   NOT NULL CHECK (status IN ('PROCESSING', 'SHIPPED', 'DELIVERED', 'DELAYED', 'CANCELLED')),
    total_amount NUMERIC(10,2) NOT NULL,
    ordered_at   TIMESTAMPTZ   NOT NULL,
    delivered_at TIMESTAMPTZ,
    tracking_id  VARCHAR(50)
);
CREATE INDEX idx_orders_customer ON orders (customer_id);

CREATE TABLE conversations (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT      NOT NULL REFERENCES users (id),
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ESCALATED', 'RESOLVED')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_conversations_customer ON conversations (customer_id);

CREATE TABLE messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES conversations (id),
    sender          VARCHAR(20) NOT NULL CHECK (sender IN ('USER', 'AI', 'HUMAN_AGENT')),
    content         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_conversation ON messages (conversation_id);

CREATE TABLE agent_actions (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL REFERENCES conversations (id),
    tool_name       VARCHAR(50) NOT NULL,
    input_json      TEXT        NOT NULL,
    result_json     TEXT        NOT NULL,
    success         BOOLEAN     NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_agent_actions_conversation ON agent_actions (conversation_id);

CREATE TABLE tickets (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT       NOT NULL REFERENCES conversations (id),
    subject         VARCHAR(200) NOT NULL,
    description     TEXT         NOT NULL,
    priority        VARCHAR(10)  NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_tickets_conversation ON tickets (conversation_id);

CREATE TABLE refund_recommendations (
    id         BIGSERIAL PRIMARY KEY,
    ticket_id  BIGINT        NOT NULL REFERENCES tickets (id),
    order_id   BIGINT        NOT NULL REFERENCES orders (id),
    amount     NUMERIC(10,2) NOT NULL,
    reason     TEXT          NOT NULL,
    status     VARCHAR(20)   NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    decided_by BIGINT REFERENCES users (id),
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_refunds_ticket ON refund_recommendations (ticket_id);
CREATE INDEX idx_refunds_status ON refund_recommendations (status);

CREATE TABLE kb_ingestion (
    id           BIGSERIAL PRIMARY KEY,
    doc_set_hash VARCHAR(64) NOT NULL,
    ingested_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);