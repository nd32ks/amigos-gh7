import { NextResponse } from "next/server";
import { ensureSession } from "@/lib/store";
import { processElderTurn, armProbe } from "@/lib/agent";
import { getFactById, addDashboardEvent } from "@/lib/store";
import { getIo } from "@/lib/socket";
import type { Fact, Session } from "@/lib/types";

function armFact(session: Session, factId: string): Fact | null {
  const fact = getFactById(factId);
  if (!fact) return null;
  armProbe(session, fact);
  return fact;
}

export async function POST(request: Request) {
  try {
    const body = (await request.json()) as { scenario?: string; session_id?: string };
    const scenario = body.scenario || "t1_miss";
    const session = ensureSession(body.session_id);
    const io = getIo();

    let result;

    if (scenario === "t1_miss") {
      // Pre-seed two exact recalls so the CRI snapshot matches the spec example (33.3).
      armFact(session, "T2_BREAKFAST_TODAY");
      await processElderTurn(session, "Bubur ayam", 1.0, {
        verdict: "exact",
        confidence: 0.95,
        recalled_value: "Bubur ayam",
        reasoning_short: "Correctly recalled breakfast.",
      });
      armFact(session, "T3_GARDENING_HOBBY");
      await processElderTurn(session, "Anggrek bulan saya sudah berbunga", 1.0, {
        verdict: "exact",
        confidence: 0.95,
        recalled_value: "Anggrek bulan saya sudah berbunga",
        reasoning_short: "Correctly recalled orchid hobby.",
      });
      armFact(session, "T1_SPOUSE_NAME");
      result = await processElderTurn(session, "Saya tidak ingat... siapa ya?", 1.0, {
        verdict: "miss",
        confidence: 0.94,
        recalled_value: "Saya tidak ingat... siapa ya?",
        reasoning_short: "Cannot recall spouse name.",
      });
    } else if (scenario === "t2_warning") {
      // Seed one prior T2 miss so the warning threshold fires on the second miss.
      const priorTs = new Date();
      priorTs.setDate(priorTs.getDate() - 1);
      const f = getFactById("T2_LAST_FAMILY_VISIT")!;
      addDashboardEvent({
        ts: priorTs.toISOString(),
        fact_id: f.fact_id,
        tier: f.tier,
        verdict: "miss",
        raw_points: -4,
        cri_credit: 0,
        humanized: "Couldn't recall kunjungan Dewi",
      });
      armFact(session, "T2_LAST_FAMILY_VISIT");
      result = await processElderTurn(session, "Tidak ada yang datang minggu ini.", 1.0, {
        verdict: "miss",
        confidence: 0.9,
        recalled_value: "Tidak ada yang datang minggu ini.",
        reasoning_short: "States no family visit occurred.",
      });
    } else if (scenario === "match_found") {
      result = await processElderTurn(session, "Saya senang merawat anggrek di teras.", 1.0);
    } else {
      return NextResponse.json({ error: "Unknown scenario" }, { status: 400 });
    }

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
      scenario,
      result,
    });
  } catch (err) {
    console.error("/api/v1/demo/trigger error:", err);
    return NextResponse.json({ error: "Demo trigger failed" }, { status: 500 });
  }
}
