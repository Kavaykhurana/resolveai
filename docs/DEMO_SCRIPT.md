# ResolveAI — 3-Minute Demo Script

Storyboard for the hackathon demo video. Total runtime ≈ 3:00.
Record with two browser windows side by side: customer chat (left), agent dashboard (right).

| Time | Scene | What to show | Narration (gist) |
|---|---|---|---|
| 0:00–0:20 | **Problem & pitch** | Title slide / landing login page | "Support teams drown in repeat tickets. ResolveAI is an agentic AI that doesn't just chat — it checks orders, creates tickets, recommends refunds, and knows when to hand off to a human." |
| 0:20–0:35 | **Stack flash** | Architecture diagram (README) | "React + Tailwind, Spring Boot + Spring AI, Gemini 2.5 Flash, PostgreSQL + pgvector RAG, JWT security — deployed on Vercel and Render." |
| 0:35–0:55 | **RAG grounding** | Customer asks: *"What is your return and refund policy?"* | "Policy answers are retrieved from a vector knowledge base — grounded, with the policy cited. No hallucinated policies." Point at the streamed answer mentioning the 30-day window. |
| 0:55–1:15 | **Tool calling: order check** | *"Where is my order SE-1002?"* | "The agent decides to call checkOrderStatus. Watch the action card — that's a real database lookup, ownership-checked, streamed live." Show SHIPPED + tracking TRK-88421-IN. |
| 1:15–1:40 | **Autonomous refund recommendation** | *"I want a refund for SE-1003, the item wasn't as described."* | "The agent validates eligibility — delivered 5 days ago, within the 30-day window — and creates a refund recommendation. Crucially, it does NOT execute the refund: it's PENDING human approval." Show the action card + ticket. |
| 1:40–2:05 | **Human-in-the-loop** | Switch to agent dashboard; open the ticket; click **Approve** | "A human agent reviews the AI's recommendation and approves with one click. The decision is recorded with who and when." Switch back: customer chat shows the approval message; conversation RESOLVED. |
| 2:05–2:25 | **Guardrails** | *"Refund SE-1004 please"* (delivered 40 days ago) | "Outside policy? The tool refuses with the exact policy reason — the agent can't be talked into breaking rules." |
| 2:25–2:45 | **Escalation** | *"My order arrived damaged. I want to talk to a human."* | "Complex or emotional issues escalate automatically: HIGH-priority ticket, escalation banner, and the human agent replies right in the same chat." Type a quick human reply from the dashboard. |
| 2:45–3:00 | **Wrap** | GitHub repo + live URLs | "Full audit trail of every AI action, 23 integration tests, live on Render + Vercel. Code and architecture docs on GitHub. ResolveAI — autonomy with accountability." |

## Recording checklist

- [ ] Both demo accounts logged in (customer@demo.com / agent@demo.com, password demo1234)
- [ ] Fresh conversation for the recording (click "+ New conversation")
- [ ] Backend has a valid `GEMINI_API_KEY` and KB ingestion completed
- [ ] Screen recording at 1080p; browser zoom ~110% for readability
- [ ] Keep each AI response wait trimmed in editing (streams take a few seconds)