# ResolveAI — Design Specification

**Date:** 2026-11-16
**Status:** Approved (approach A: Spring AI native tool calling)
**Purpose:** Autonomous AI customer support agent satisfying the AI Builder Hackathon and Gen-AI Product Hackathon (Product Space / Unstop) submission requirements: working agentic prototype, live demo, GitHub codebase, documented problem/solution/architecture, 3-minute demo video.

## 1. Problem & Solution

Customer support teams drown in repetitive queries (order status, return policies, refunds) while complex complaints wait in queues. ResolveAI is an AI support agent that:

- **Understands** queries using an LLM (Gemini) grounded in company documents via RAG (semantic search over pgvector).
- **Acts autonomously** through function-calling tools: checks order status, creates tickets, recommends refunds, escalates to humans.
- **Keeps humans in the loop**: refunds are recommendations that a human agent approves/rejects; escalations hand the full transcript to a human who can reply inside the same conversation.

Demo company: **ShopEase**, a fictional e-commerce store with seeded orders and policy documents.

## 2. Architecture

Monorepo, two deployables, one database:

```
React SPA (Vite + Tailwind CSS) ── Vercel
   │ REST + SSE, JWT bearer auth
Spring Boot API ── Render (Docker)
   ├─ Auth module      (Spring Security + JWT)
   ├─ Chat module      (Spring AI ChatClient → Gemini API)
   │    ├─ RAG advisor → PgVectorStore (cosine similarity)
   │    └─ Tools: checkOrderStatus / createTicket /
   │             recommendRefund / escalateToHuman
   ├─ Ticket module    (agent dashboard APIs)
   └─ Order module     (seeded demo orders)
PostgreSQL + pgvector ── Render
```

- Single Spring Boot service (modular packages, not microservices).
- Chat responses stream via SSE: token events + `agent_action` events. Every tool call is persisted (`agent_actions`) and rendered as an action card in the UI — the visible audit trail of autonomous behavior.
- Document ingestion at startup: seeded markdown docs under `backend/src/main/resources/kb/` are chunked (TokenTextSplitter), embedded with Gemini `gemini-embedding-001`, stored in pgvector. Idempotent: skipped when the vector store already contains the document set (tracked via a `kb_ingestion` marker row).
- Conversation memory: full message history loaded from DB per turn and passed to ChatClient (no in-memory state; survives restarts).

## 3. Tech Stack (fixed by project brief)

| Component | Technology |
|---|---|
| Frontend | React.js (Vite) + Tailwind CSS, light theme |
| Backend | Java 21 + Spring Boot 3.5.x |
| AI integration / agent | Spring AI 1.1.x (`spring-ai-starter-model-google-genai`) |
| LLM | Gemini (`gemini-2.5-flash`) via Gemini Developer API key |
| Embeddings | Gemini `gemini-embedding-001` (768 dims) |
| RAG / vector DB | Spring AI PgVectorStore + PostgreSQL pgvector |
| ORM | Spring Data JPA + Hibernate |
| Migrations | Flyway |
| Security | Spring Security + JWT (jjwt) |
| Build | Maven (backend), npm (frontend) |
| Deployment | Render (backend + Postgres), Vercel (frontend) |
| VCS | Git + GitHub |

## 4. Data Model

All tables in one PostgreSQL database; Flyway owns the schema.

| Table | Key columns |
|---|---|
| `users` | id, email (unique), password_hash, name, role (`CUSTOMER`/`AGENT`), created_at |
| `orders` | id, order_number (unique), customer_id FK, items (jsonb), status (`PROCESSING`/`SHIPPED`/`DELIVERED`/`DELAYED`/`CANCELLED`), total_amount, ordered_at, delivered_at, tracking_id |
| `conversations` | id, customer_id FK, status (`ACTIVE`/`ESCALATED`/`RESOLVED`), created_at, updated_at |
| `messages` | id, conversation_id FK, sender (`USER`/`AI`/`HUMAN_AGENT`), content, created_at |
| `agent_actions` | id, conversation_id FK, tool_name, input_json, result_json, success, created_at |
| `tickets` | id, conversation_id FK, subject, description, priority (`LOW`/`MEDIUM`/`HIGH`), status (`OPEN`/`IN_REVIEW`/`RESOLVED`), created_at, updated_at |
| `refund_recommendations` | id, ticket_id FK, order_id FK, amount, reason, status (`PENDING`/`APPROVED`/`REJECTED`), decided_by FK nullable, decided_at, created_at |
| `kb_ingestion` | id, doc_set_hash, ingested_at (idempotency marker) |
| `vector_store` | Spring AI PgVectorStore managed: id, content, metadata, embedding vector(768) |

Seed data (Flyway `V2__seed.sql` + startup runner for demo users' password hashes):
- Demo users: `customer@demo.com` / `agent@demo.com`, password `demo1234` (BCrypt).
- ~8 orders for the demo customer spanning all statuses, incl. one delivered 5 days ago (refund-eligible) and one delivered 40 days ago (outside window).
- 6 KB docs: return & refund policy (30-day window, conditions), shipping policy, warranty FAQ, payment & billing FAQ, cancellation policy, contact/escalation policy.

## 5. Agent Behavior

Per customer message:
1. Load conversation history from DB.
2. RAG advisor embeds the query, retrieves top-4 chunks (similarity threshold 0.55), injects them with source names.
3. System prompt: ResolveAI persona; answer only from grounded context for policy questions; cite policy names; use tools for actions; never fabricate order data; escalate on explicit request, repeated failure, out-of-policy complaints, or low confidence.
4. ChatClient executes Gemini tool calls in a loop; each call logged + streamed; final answer streamed and persisted.

Tools (Spring AI `@Tool` methods, customer-scoped):

| Tool | Behavior |
|---|---|
| `checkOrderStatus(orderNumber)` | Ownership-checked lookup of the caller's order; returns status, items, tracking, dates. |
| `createTicket(subject, description, priority)` | Ticket linked to conversation. |
| `recommendRefund(orderNumber, amount, reason)` | Validates order exists, belongs to caller, delivered within 30-day window, amount ≤ order total; creates PENDING recommendation + linked ticket. Never executes refunds. |
| `escalateToHuman(reason)` | Conversation → ESCALATED; HIGH-priority ticket with AI summary; customer informed. |

Human side (AGENT role only): list tickets/conversations, read transcript + action trail, approve/reject refunds (decision persisted with agent identity), reply into conversation (HUMAN_AGENT message visible in customer chat), resolve tickets (conversation → RESOLVED).

## 6. API

JWT-protected except auth + health. Role checks server-side.

| Endpoint | Role | Purpose |
|---|---|---|
| `POST /api/auth/register` | public | customer self-registration → JWT |
| `POST /api/auth/login` | public | → JWT + role |
| `GET /api/orders` | CUSTOMER | own orders |
| `POST /api/conversations` | CUSTOMER | start conversation |
| `GET /api/conversations` | CUSTOMER | own conversations |
| `GET /api/conversations/{id}/messages` | owner/AGENT | transcript + actions |
| `POST /api/chat/{conversationId}` | owner | SSE stream: `token`, `action`, `done`, `error` events |
| `GET /api/agent/tickets` | AGENT | all tickets (+ refund recommendations) |
| `GET /api/agent/conversations` | AGENT | escalated/all conversations |
| `POST /api/agent/tickets/{id}/reply` | AGENT | human reply into conversation |
| `POST /api/agent/refunds/{id}/decide` | AGENT | `{decision: APPROVED|REJECTED}` |
| `POST /api/agent/tickets/{id}/resolve` | AGENT | resolve ticket + conversation |
| `GET /api/health` | public | Render health check |

Error handling: Gemini timeout/rate-limit → SSE `error` event with friendly retry message, history preserved; tool failure → logged failed action, agent apologizes and offers escalation; validation errors → 400 with message; auth failures → 401/403. No silent failures.

## 7. Frontend (light theme)

Pages:
- **Login/Register** — demo credentials shown on the login card for instant judge access.
- **Customer chat** — conversation list, streaming chat, action cards ("Checked order #SE-1042 → SHIPPED"), escalation banner, human-agent replies visually distinct, suggested demo prompts.
- **Agent dashboard** — ticket queue with priority/status filters, conversation transcript + action trail, refund approval panel (approve/reject with AI justification), reply box, resolve action.

State: React hooks + fetch; SSE via fetch ReadableStream (POST-compatible). Tailwind utility styling, no extra UI libraries.

## 8. Testing & Verification

- Backend: JUnit tests for auth (register/login/JWT filter), order ownership, refund eligibility rules, ticket/refund decision flow (mocked ChatModel for agent-loop tests; real Gemini smoke test when key present).
- Frontend: production build + browser verification of golden paths (login → chat → order check → refund recommendation → agent approval → human reply) and edge cases (wrong order ownership, out-of-window refund, escalation).
- Database considered complete only after migrations, CRUD, relationships, and app integration verified end-to-end against local PostgreSQL+pgvector, then against Render Postgres.

## 9. Deployment & Configuration

- Render: PostgreSQL (pgvector enabled via `CREATE EXTENSION`), web service from `backend/Dockerfile` (multi-stage Maven build, Java 21).
- Vercel: frontend static build, `VITE_API_URL` env var; SPA rewrites.
- Env vars (never committed): `GEMINI_API_KEY`, `DATABASE_URL`/`DB_*`, `JWT_SECRET`, `CORS_ORIGIN` (backend); `VITE_API_URL` (frontend). `.env.example` files document them.
- CORS restricted to the deployed frontend origin (+ localhost dev).

## 10. Hackathon Deliverables

1. Public GitHub repo with meaningful commit history.
2. Detailed README: overview, features, architecture, stack, setup, env vars, DB config, API docs, usage, testing, deployment.
3. Architecture doc (this spec, polished into `docs/ARCHITECTURE.md`) + problem/solution explanation.
4. Live deployed demo (Render + Vercel) with seeded demo accounts.
5. 3-minute demo video script (`docs/DEMO_SCRIPT.md`) covering: problem → RAG answer → autonomous order check → refund recommendation → human approval → escalation → architecture.
6. AI-contribution note in README distinguishing human-directed design from AI-generated code (Gen-AI hackathon rule).