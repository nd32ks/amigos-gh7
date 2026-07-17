import { NextResponse } from "next/server";
import { ensureSession } from "@/lib/store";
import { processCompanionTurn, processElderTurn, startSession } from "@/lib/agent";
import { getIo } from "@/lib/socket";
import type { JudgeResult } from "@/lib/types";

export async function POST(request: Request) {
  try {
    const body = (await request.json()) as {
      session_id?: string;
      role: "elder" | "companion";
      text?: string;
      probe_fact_id?: string;
      stt_confidence?: number;
      ts?: string;
      forced_verdict?: Partial<JudgeResult>;
    };

    const io = getIo();

    if (body.role === "companion") {
      if (body.session_id) {
        const session = ensureSession(body.session_id);
        processCompanionTurn(session, body.text || "", body.probe_fact_id);
        return NextResponse.json({ session_id: session.session_id });
      }
      const started = startSession();
      const session = ensureSession(started.session_id);
      if (body.text) {
        processCompanionTurn(session, body.text, started.probe_fact_id);
      }
      return NextResponse.json({
        session_id: started.session_id,
        companion: {
          text: started.companion_text,
          probe_fact_id: started.probe_fact_id,
        },
      });
    }

    const session = ensureSession(body.session_id);
    const result = await processElderTurn(
      session,
      body.text || "",
      typeof body.stt_confidence === "number" ? body.stt_confidence : 1.0,
      body.forced_verdict
    );

    if (io) {
      if (result.escalation === "acute") {
        io.emit("alert:acute_t1", result.alert || { session_id: session.session_id });
      } else if (result.escalation === "warning") {
        io.emit("alert:warning", {
          text: "Noticeable decline in recent event recall this week",
          ewma_delta_7d: result.ewma_7d,
          ts: new Date().toISOString(),
        });
      }
      if (result.match?.matched) {
        io.emit("match:found", result.match);
      }
      io.emit("trend:update", {
        date: new Date().toISOString().slice(0, 10),
        cri: result.cri_session,
        ewma: result.ewma_7d,
        probes: session.cri_events.length,
      });
    }

    return NextResponse.json({
      session_id: session.session_id,
      verdict: result.verdict,
      escalation: result.escalation,
      companion_directive: result.companion_directive,
      companion: {
        text: result.companion_text,
        probe_fact_id: result.probe_fact_id,
      },
      match: result.match,
      cri_session: result.cri_session,
      ewma_7d: result.ewma_7d,
    });
  } catch (err) {
    console.error("/api/v1/turn error:", err);
    return NextResponse.json({ error: "Failed to process turn" }, { status: 500 });
  }
}
