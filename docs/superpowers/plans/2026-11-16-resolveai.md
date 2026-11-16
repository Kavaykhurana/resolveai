# ResolveAI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Execution mode: inline (user directed full autonomous completion; general-purpose subagents disabled in this session).

**Goal:** Build, verify, and deploy ResolveAI — an autonomous AI customer support agent (RAG + tool calling + human-in-the-loop) satisfying both Product Space hackathons.

**Architecture:** Single Spring Boot API (Spring AI ChatClient → Gemini, manual RAG retrieval from PgVectorStore, @Tool methods with per-request ToolContext) + React/Vite/Tailwind SPA + one PostgreSQL/pgvector database. SSE streaming chat with persisted tool-call action events.

**Tech Stack:** Java 21, Spring Boot 3.5.x, Spring AI 1.1.x (google-genai + pgvector starters), Flyway, jjwt 0.12, React 19 + Vite + Tailwind 4, Maven, npm.

**Spec:** `docs/superpowers/specs/2026-11-16-resolveai-design.md`

## Global Constraints

- Java 21 toolchain (`JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`); Spring Boot 3.5.x; Spring AI BOM 1.1.x (verify latest 1.1 GA on Maven Central before pinning).
- LLM `gemini-2.5-flash`; embeddings `text-embedding-004`, 768 dimensions, cosine distance, HNSW index.
- Secrets only via env vars: `GEMINI_API_KEY`, `JWT_SECRET`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `CORS_ORIGIN`, `PORT`; frontend `VITE_API_URL`. Never commit secrets; `.env.example` documents names only.
- Light theme UI. Demo accounts `customer@demo.com` / `agent@demo.com`, password `demo1234`.
- Demo company "ShopEase"; order numbers `SE-1001`…`SE-1008`; refund window 30 days from delivery.
- Local dev DB: `jdbc:postgresql://localhost:5432/resolveai`, user `kavaykhurana`, trust auth (Homebrew postgresql@18 + pgvector already installed and running).
- Meaningful git commits per task. No features beyond the spec.

---

### Task 1: Repo scaffolding + database + Flyway schema & seed

**Files:**
- Create: `.gitignore`, `backend/pom.xml`, `backend/src/main/resources/application.yml`, `backend/src/main/resources/db/migration/V1__schema.sql`, `V2__seed.sql`, `backend/src/main/java/com/resolveai/ResolveAiApplication.java`
- Create: `backend/.env.example`, `frontend/.env.example` (frontend dir created in Task 6)

**Interfaces:**
- Produces: tables `users, orders, conversations, messages, agent_actions, tickets, refund_recommendations, kb_ingestion` exactly as spec §4; `vector_store` created by PgVectorStore (`initialize-schema: true`), not Flyway.
- Produces: Maven coordinates `com.resolveai:resolveai-backend`, package root `com.resolveai`.

**Key content:**
- `V1__schema.sql`: `CREATE EXTENSION IF NOT EXISTS vector;` then all tables with FKs, enums as VARCHAR + CHECK constraints, `items JSONB` on orders, indexes on FKs and `orders.order_number`.
- `V2__seed.sql`: 2 users with BCrypt hash of `demo1234` (generate via `htpasswd -bnBC 10 "" demo1234`, convert `$2y`→`$2a`); 8 orders for customer id 1: SE-1001 PROCESSING, SE-1002 SHIPPED (tracking), SE-1003 DELIVERED 5 days ago (refund-eligible, total 2499.00), SE-1004 DELIVERED 40 days ago (out of window), SE-1005 DELAYED, SE-1006 CANCELLED, SE-1007 DELIVERED 2 days ago, SE-1008 PROCESSING. Items jsonb e.g. `[{"name":"Wireless Headphones","qty":1,"price":2499.00}]`.
- `pom.xml` deps: web, data-jpa, security, validation, jdbc, flyway-core, flyway-database-postgresql, postgresql, jjwt-api/impl/jackson (0.12.6), lombok NOT used (plain Java records/classes), spring-ai-starter-model-google-genai, spring-ai-starter-vector-store-pgvector, spring-boot-starter-test, spring-security-test. Spring AI BOM via dependencyManagement.
- `application.yml`: datasource from env with local defaults; `spring.ai.google.genai.api-key: ${GEMINI_API_KEY:dummy}`, chat model gemini-2.5-flash, embedding text-embedding-004; pgvector initialize-schema true, dimensions 768; `app.jwt.secret: ${JWT_SECRET:dev-secret-change-me-32-chars-min!!}`, `app.cors.origin: ${CORS_ORIGIN:http://localhost:5173}`, `app.kb.ingest-enabled: ${KB_INGEST:true}`, `server.port: ${PORT:8080}`.

**Steps:**
- [ ] Create local DB: `createdb resolveai` (extension created by Flyway).
- [ ] Write pom/app/yml/migrations; generate bcrypt hash; write seed.
- [ ] `cd backend && JAVA_HOME=... mvn -q spring-boot:run` boots, Flyway migrates (verify `\dt` via psql), then stop.
- [ ] Verify seed: `psql resolveai -c "SELECT order_number,status FROM orders;"` shows 8 rows.
- [ ] Commit `feat: scaffold Spring Boot backend with Flyway schema and ShopEase seed data`.

### Task 2: Auth (JWT + Spring Security) with tests

**Files:**
- Create: `backend/src/main/java/com/resolveai/auth/` → `AuthController.java`, `AuthService.java`, `JwtService.java`, `JwtAuthFilter.java`, `SecurityConfig.java`, `dto/AuthRequest.java`, `dto/AuthResponse.java`, `dto/RegisterRequest.java`
- Create: `backend/src/main/java/com/resolveai/user/` → `User.java` (JPA entity), `UserRepository.java`, `Role.java` enum
- Create: `backend/src/test/java/com/resolveai/auth/AuthFlowTest.java`

**Interfaces:**
- Produces: `POST /api/auth/register {name,email,password}` → 200 `{token, role, name}` (role CUSTOMER); `POST /api/auth/login {email,password}` → same; bad creds → 401.
- Produces: `JwtService.generateToken(User) / parseToken(String) → claims(subject=email, role)`; `JwtAuthFilter` sets `Authentication` with authority `ROLE_CUSTOMER|ROLE_AGENT`; `SecurityConfig`: stateless, permit `/api/auth/**`, `/api/health`, `/error`; `/api/agent/**` hasRole AGENT; rest authenticated; CORS from `app.cors.origin`; CSRF disabled.
- Produces: `UserRepository.findByEmail(String) → Optional<User>`; `User` fields per spec (table `users`, column `password_hash`).
- Consumes: nothing (first code task).

**Steps:**
- [ ] Write `AuthFlowTest` (`@SpringBootTest` + `MockMvc`, `@Transactional`): register→login→access `/api/orders` with token 200 / without 401; wrong password 401; agent-only route as customer 403. Include `spring.ai.google.genai.api-key=dummy` and `app.kb.ingest-enabled=false` via `@TestPropertySource` (test profile `application-test.yml`).
- [ ] Run → fails (404/no beans). Implement entities, JWT, filter, config, controller (BCrypt).
- [ ] Run → pass. Commit `feat: JWT authentication with role-based security and demo accounts`.

### Task 3: Orders + conversations + messages domain

**Files:**
- Create: `backend/src/main/java/com/resolveai/order/` → `Order.java`, `OrderRepository.java`, `OrderController.java`, `OrderDto.java`
- Create: `backend/src/main/java/com/resolveai/chat/` → `Conversation.java`, `ConversationRepository.java`, `Message.java`, `MessageRepository.java`, `ConversationController.java`, `dto/ConversationDto.java`, `dto/MessageDto.java`
- Test: `backend/src/test/java/com/resolveai/order/OrderAccessTest.java`

**Interfaces:**
- Produces: `GET /api/orders` → customer's own orders as `OrderDto(orderNumber, items, status, totalAmount, orderedAt, deliveredAt, trackingId)`.
- Produces: `POST /api/conversations` → `{id, status, createdAt}`; `GET /api/conversations` → own list; `GET /api/conversations/{id}/messages` → `{messages:[MessageDto(id,sender,content,createdAt)], actions:[ActionDto(id,toolName,inputJson,resultJson,success,createdAt)]}` — owner or AGENT only, else 403.
- Produces: `OrderRepository.findByOrderNumberAndCustomerId(String, Long)`; `findByCustomerId(Long)`; `ConversationRepository.findByCustomerIdOrderByUpdatedAtDesc(Long)`; `MessageRepository.findByConversationIdOrderByCreatedAtAsc(Long)`.
- Entities: `Conversation.status` enum ACTIVE/ESCALATED/RESOLVED; `Message.sender` enum USER/AI/HUMAN_AGENT.

**Steps:**
- [ ] Failing test: customer sees only own orders; other customer's conversation transcript → 403.
- [ ] Implement; tests pass; commit `feat: orders and conversation domain with ownership enforcement`.

### Task 4: Agent tools + action trail (no LLM yet)

**Files:**
- Create: `backend/src/main/java/com/resolveai/agent/` → `SupportTools.java`, `AgentAction.java`, `AgentActionRepository.java`, `Ticket.java`, `TicketRepository.java`, `RefundRecommendation.java`, `RefundRecommendationRepository.java`
- Create: `backend/src/main/java/com/resolveai/chat/ActionEvent.java` (record: toolName, summary, success)
- Test: `backend/src/test/java/com/resolveai/agent/SupportToolsTest.java`

**Interfaces:**
- Consumes: repositories from Task 3; `ToolContext` keys `"customerId"` (Long), `"conversationId"` (Long), `"actionSink"` (`Consumer<ActionEvent>`).
- Produces `SupportTools` methods (Spring AI `@Tool` annotations, descriptions written for the LLM):
  - `String checkOrderStatus(String orderNumber, ToolContext ctx)`
  - `String createTicket(String subject, String description, String priority, ToolContext ctx)`
  - `String recommendRefund(String orderNumber, double amount, String reason, ToolContext ctx)`
  - `String escalateToHuman(String reason, ToolContext ctx)`
- Behavior per spec §5: ownership checks return textual error the LLM can relay; recommendRefund validates delivered + within 30 days + amount ≤ total, creates ticket (subject "Refund request for SE-xxxx", MEDIUM) + PENDING recommendation; escalate sets conversation ESCALATED + HIGH ticket. Every call persists `AgentAction` (input/result JSON via Jackson ObjectMapper) and emits ActionEvent to sink.
- Produces: `TicketRepository.findAllByOrderByCreatedAtDesc()`, `RefundRecommendationRepository.findByStatusOrderByCreatedAtDesc(String)` etc. for Task 5.

**Steps:**
- [ ] Failing tests (plain `@SpringBootTest @Transactional`, call tools directly with a ToolContext): happy order check; foreign order → "not found" text; eligible refund creates PENDING row; out-of-window refund refused; escalation flips conversation status + HIGH ticket.
- [ ] Implement; pass; commit `feat: agent tools with action trail, tickets and refund recommendations`.

### Task 5: Agent dashboard APIs

**Files:**
- Create: `backend/src/main/java/com/resolveai/agent/AgentController.java`, `dto/TicketDto.java`, `dto/RefundDto.java`, `dto/DecisionRequest.java`, `dto/ReplyRequest.java`
- Test: `backend/src/test/java/com/resolveai/agent/AgentControllerTest.java`

**Interfaces:**
- Consumes: Task 4 repositories, Task 3 message repo, JWT principal (email → User).
- Produces:
  - `GET /api/agent/tickets` → `[{id, conversationId, subject, description, priority, status, createdAt, refund: {id, orderNumber, amount, reason, status} | null}]`
  - `GET /api/agent/conversations` → all conversations w/ customer name + status
  - `POST /api/agent/tickets/{id}/reply {content}` → persists HUMAN_AGENT message in linked conversation, ticket → IN_REVIEW
  - `POST /api/agent/refunds/{id}/decide {decision:"APPROVED"|"REJECTED"}` → sets status, decidedBy, decidedAt; only PENDING decidable (else 409); adds HUMAN_AGENT message "Your refund of ₹X for SE-xxxx was approved/rejected."
  - `POST /api/agent/tickets/{id}/resolve` → ticket RESOLVED + conversation RESOLVED

**Steps:**
- [ ] Failing MockMvc tests as agent: decide pending refund → APPROVED + message appears; double-decide → 409; reply → HUMAN_AGENT message; customer role → 403.
- [ ] Implement; pass; commit `feat: agent dashboard APIs with human-in-the-loop refund decisions`.

### Task 6: RAG ingestion + streaming chat with Gemini

**Files:**
- Create: `backend/src/main/resources/kb/` → `return-refund-policy.md`, `shipping-policy.md`, `warranty-faq.md`, `payment-billing-faq.md`, `cancellation-policy.md`, `escalation-policy.md` (realistic ShopEase content; refund doc states 30-day window, original payment method, conditions)
- Create: `backend/src/main/java/com/resolveai/chat/KbIngestionRunner.java`, `ChatService.java`, `ChatController.java`, `ChatRequest.java`
- Modify: `backend/src/main/java/com/resolveai/ResolveAiApplication.java` (enable async executor bean)
- Test: `backend/src/test/java/com/resolveai/chat/ChatServiceTest.java` (mocked ChatModel)

**Interfaces:**
- Consumes: `VectorStore` (PgVectorStore auto-config), `EmbeddingModel` (Google GenAI auto-config), `ChatModel` (Google GenAI auto-config), `SupportTools`, Task 3 repos.
- Produces: `KbIngestionRunner` (`ApplicationRunner`, conditional `app.kb.ingest-enabled=true`): hash doc set → compare `kb_ingestion` marker; if changed: delete old vectors (`vectorStore.delete` by filter or truncate via JdbcTemplate), `TokenTextSplitter` chunks, `Document` metadata `source=<filename>`, `vectorStore.add(...)`, upsert marker.
- Produces: `POST /api/chat/{conversationId}` body `{message}` → `SseEmitter` (timeout 120s). Flow in `ChatService.streamChat(conversationId, message, principal)`:
  1. Ownership check; persist USER message; load history (last 20 messages) as `UserMessage`/`AssistantMessage`.
  2. `vectorStore.similaritySearch(SearchRequest.builder().query(message).topK(4).similarityThreshold(0.55).build())` → context block with `[source]` labels.
  3. `ChatClient.builder(chatModel).build().prompt().system(SYSTEM_PROMPT + grounded context).messages(history).user(message).tools(supportTools).toolContext(Map.of("customerId",…,"conversationId",…,"actionSink",sink)).stream().content()` on bounded elastic scheduler.
  4. SSE events: `token` (text chunk), `action` (ActionEvent JSON from sink), `done` (full text persisted as AI message), `error` (friendly message; history preserved).
- SYSTEM_PROMPT: ResolveAI persona, ground policy answers in context + cite source, use tools for actions, never invent order data, recommendRefund never executes, escalate per spec triggers, currency ₹, concise friendly tone.
- Tests: mock `ChatModel` (MockitoBean) returning canned Flux; mock VectorStore search → empty; verify USER+AI messages persisted and SSE completes; Gemini live smoke test deferred to Task 8 (needs real key).

**Steps:**
- [ ] Write KB docs; failing ChatServiceTest; implement ingestion + chat; pass.
- [ ] Manual boot with real key if available (else dummy): ingestion logs chunk count; `psql -c "SELECT count(*) FROM vector_store;"` > 0.
- [ ] Commit `feat: RAG knowledge base ingestion and streaming agentic chat via Gemini`.

### Task 7: Frontend (React + Tailwind, light theme)

**Files:**
- Create: `frontend/` via `npm create vite@latest . -- --template react`; add `tailwindcss @tailwindcss/vite react-router-dom`
- Create: `frontend/src/` → `api.js` (fetch wrapper w/ JWT, `streamChat` SSE parser), `AuthContext.jsx`, `App.jsx` (routes + guards), `pages/Login.jsx`, `pages/Register.jsx`, `pages/Chat.jsx`, `pages/Dashboard.jsx`, `components/ActionCard.jsx`, `components/MessageBubble.jsx`, `index.css` (tailwind import + light theme tokens)
- Create: `frontend/vercel.json` (SPA rewrite), `frontend/.env.example` (`VITE_API_URL`)

**Interfaces:**
- Consumes: Task 2–6 API exactly as specified above; SSE events `token|action|done|error`.
- Login page shows demo creds; role-based redirect (CUSTOMER→/chat, AGENT→/dashboard). Chat: conversation sidebar, streaming bubbles, ActionCard per action event, escalation banner when status ESCALATED, suggested prompts ("Where is my order SE-1002?", "I want a refund for SE-1003", "The product arrived damaged, I want to speak to a human"). Dashboard: ticket list + detail (transcript, actions, refund approve/reject, reply box, resolve); polls every 5s for new tickets/messages.
- `streamChat(conversationId, message, {onToken,onAction,onDone,onError})` uses fetch POST + ReadableStream, parses `event:`/`data:` lines.

**Steps:**
- [ ] Scaffold; `npm run build` passes with placeholder routes; commit scaffold.
- [ ] Implement api/auth/pages; `npm run build` clean.
- [ ] Browser verification happens in Task 8. Commit `feat: React frontend with streaming chat and agent dashboard`.

### Task 8: End-to-end local verification

**Steps:**
- [ ] Backend tests all green: `mvn test`.
- [ ] Boot backend (real `GEMINI_API_KEY` if provided; else request from user — hard blocker for live AI) + `npm run dev`.
- [ ] Browser (Playwright MCP) golden path: login customer → ask policy question (RAG answer w/ citation) → "Where is SE-1002?" (action card SHIPPED) → refund SE-1003 (PENDING recommendation card) → login agent → approve refund → customer sees decision message → escalate scenario → agent replies → customer sees HUMAN_AGENT reply.
- [ ] Edge cases: refund SE-1004 refused (out of window); ask about another customer's order → refused; register new account works.
- [ ] DB end-to-end checklist: migrations, CRUD, FK relationships, vector search — verified via psql + app behavior.
- [ ] Fix any failures; commit `test: verify end-to-end flows and fix issues`.

### Task 9: GitHub + README + hackathon docs

**Files:**
- Create: `README.md` (overview, features, architecture diagram, stack table, local setup, env var table, DB config, full API docs, usage w/ demo accounts, testing, deployment, AI-contribution note), `docs/ARCHITECTURE.md` (polished spec), `docs/DEMO_SCRIPT.md` (3-min video storyboard), `LICENSE` (MIT), `render.yaml`, `backend/Dockerfile`
- `backend/Dockerfile`: multi-stage `maven:3.9-eclipse-temurin-21` → `eclipse-temurin:21-jre`, `EXPOSE 8080`, honors `PORT`.
- `render.yaml`: postgres `resolveai-db` (plan free) + web `resolveai-api` (docker, envVars: GEMINI_API_KEY sync:false, JWT_SECRET generateValue:true, DB_* from database, CORS_ORIGIN sync:false).

**Steps:**
- [ ] Write docs; `gh repo create Kavaykhurana/resolveai --public --source . --push` (meaningful history already present).
- [ ] Verify repo live; commit docs first `docs: README, architecture, demo script and deployment config`.

### Task 10: Deploy Render + Vercel, verify live

**Blockers to request from user (single consolidated ask):** `render login`, `vercel login`, `GEMINI_API_KEY` value exported locally (never committed).

**Steps:**
- [ ] Create Render Postgres (pgvector), enable extension, note connection vars.
- [ ] Deploy backend via render.yaml/CLI; set GEMINI_API_KEY + CORS_ORIGIN (Vercel URL); wait healthy; verify `/api/health` + login over HTTPS.
- [ ] `vercel deploy --prod` frontend with `VITE_API_URL` = Render URL; set CORS_ORIGIN accordingly; redeploy backend if needed.
- [ ] Live browser verification of the full golden path on production URLs; verify vector ingestion ran on Render.
- [ ] Update README with live URLs; final commit `docs: add live deployment URLs`.