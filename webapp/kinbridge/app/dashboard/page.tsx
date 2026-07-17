"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  ResponsiveContainer,
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ReferenceLine,
} from "recharts";
import { io } from "socket.io-client";

interface TrendPoint {
  date: string;
  cri: number;
  ewma: number;
  probes: number;
}

interface DashboardEvent {
  ts: string;
  fact_id: string;
  tier: 1 | 2 | 3;
  verdict: "exact" | "partial" | "miss" | "no_answer" | "excluded_stt";
  raw_points: number;
  cri_credit: number;
  humanized: string;
}

interface Summary {
  elder: { profile_id: string; name: string; preferred_address: string };
  today: { sessions: number; minutes_engaged: number; probes_scored: number };
  cri_latest: number;
  ewma_7d: number;
  trend_direction: string;
  active_warnings: string[];
}

interface AlertPayload {
  alert_id: string;
  created_at: string;
  trigger: { fact_id: string; recalled_value: string; verdict: string; judge_confidence: number };
  cognitive_snapshot: { cri_session: number; ewma_7d: number };
  companion_action_taken: { spoken_text: string };
  notification: { title: string; body: string };
}

export default function DashboardPage() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [trend, setTrend] = useState<TrendPoint[]>([]);
  const [events, setEvents] = useState<DashboardEvent[]>([]);
  const [alert, setAlert] = useState<AlertPayload | null>(null);

  async function fetchSummary() {
    const res = await fetch("/api/v1/dashboard/summary");
    setSummary(await res.json());
  }

  async function fetchTrend() {
    const res = await fetch("/api/v1/dashboard/trend?days=30");
    const data = await res.json();
    setTrend(data.points || []);
  }

  async function fetchEvents() {
    const res = await fetch("/api/v1/dashboard/events?limit=10");
    const data = await res.json();
    setEvents(data.events || []);
  }

  useEffect(() => {
    fetchSummary();
    fetchTrend();
    fetchEvents();

    const s = io({ path: "/ws/alerts", transports: ["websocket", "polling"] });

    s.on("alert:acute_t1", (payload: AlertPayload) => {
      setAlert(payload);
      fetchSummary();
      fetchTrend();
      fetchEvents();
    });

    s.on("alert:warning", () => {
      fetchSummary();
    });

    s.on("match:found", () => {
      fetchSummary();
    });

    s.on("trend:update", () => {
      fetchTrend();
      fetchEvents();
      fetchSummary();
    });

    return () => {
      s.disconnect();
    };
  }, []);

  const verdictDot = {
    exact: "bg-sage",
    partial: "bg-amber",
    miss: "bg-brick",
    no_answer: "bg-mist",
    excluded_stt: "bg-smoke",
  };

  return (
    <main className="min-h-screen bg-cream px-6 py-8">
      <div className="max-w-5xl mx-auto">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-8 gap-4">
          <Link href="/" className="font-display text-heading text-espresso">
            KINBRIDGE
          </Link>
          <div className="font-ui text-body text-ash">
            {summary?.elder.preferred_address || "Ibu Sri"} · Gading Serpong
          </div>
        </div>

        {summary && summary.active_warnings.length > 0 && (
          <div className="bg-amber-bg border border-amber rounded-card px-6 py-4 mb-6 flex items-start gap-3">
            <span className="text-amber text-xl">⚠</span>
            <p className="font-ui text-body text-espresso">{summary.active_warnings[0]}</p>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
          <div className="lg:col-span-2 bg-white border border-sand rounded-card p-6">
            <h2 className="font-display text-heading-sm text-espresso mb-4">
              Cognitive Wellness Trend
            </h2>
            <div className="h-72">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={trend} margin={{ top: 10, right: 20, left: 0, bottom: 0 }}>
                  <CartesianGrid stroke="#E4D9C3" strokeDasharray="3 3" />
                  <XAxis dataKey="date" tick={{ fontSize: 12, fill: "#4d4d4d" }} />
                  <YAxis domain={[0, 100]} tick={{ fontSize: 12, fill: "#4d4d4d" }} />
                  <Tooltip contentStyle={{ background: "#fff", border: "1px solid #E4D9C3" }} />
                  <ReferenceLine y={40} stroke="#E4D9C3" strokeDasharray="4 4" />
                  <Line
                    type="monotone"
                    dataKey="ewma"
                    stroke="#231A12"
                    strokeWidth={2}
                    dot={false}
                    name="EWMA 7d"
                  />
                  <Line
                    type="monotone"
                    dataKey="cri"
                    stroke="#C05A2E"
                    strokeWidth={0}
                    dot={{ r: 4, fill: "#C05A2E" }}
                    activeDot={{ r: 6 }}
                    name="Daily CRI"
                  />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>

          <div className="bg-white border border-sand rounded-card p-6">
            <h2 className="font-display text-heading-sm text-espresso mb-4">Today</h2>
            <div className="space-y-4">
              <div>
                <p className="font-ui text-caption text-mist uppercase tracking-wide">Sessions</p>
                <p className="font-display text-heading text-espresso">{summary?.today.sessions || 0}</p>
              </div>
              <div>
                <p className="font-ui text-caption text-mist uppercase tracking-wide">Minutes</p>
                <p className="font-display text-heading text-espresso">{summary?.today.minutes_engaged || 0}</p>
              </div>
              <div>
                <p className="font-ui text-caption text-mist uppercase tracking-wide">Probes</p>
                <p className="font-display text-heading text-espresso">{summary?.today.probes_scored || 0}</p>
              </div>
              <div>
                <p className="font-ui text-caption text-mist uppercase tracking-wide">Latest EWMA</p>
                <p className="font-display text-heading text-espresso">
                  {summary ? Math.round(summary.ewma_7d) : "—"}
                </p>
              </div>
            </div>
          </div>
        </div>

        <div className="bg-white border border-sand rounded-card p-6 mb-8">
          <h2 className="font-display text-heading-sm text-espresso mb-4">Recent Moments</h2>
          <div className="divide-y divide-sand">
            {events.length === 0 && (
              <p className="font-ui text-body text-ash py-4">No scored moments yet.</p>
            )}
            {events.map((ev, i) => (
              <div key={`${ev.ts}-${i}`} className="py-4 flex items-start gap-4">
                <span
                  className={`w-3 h-3 rounded-full mt-2 ${
                    verdictDot[ev.verdict] || "bg-mist"
                  }`}
                />
                <div className="flex-1">
                  <p className="font-ui text-body text-espresso">{ev.humanized}</p>
                  <p className="font-ui text-caption text-mist">
                    T{ev.tier} · {new Date(ev.ts).toLocaleTimeString("id-ID", { hour: "2-digit", minute: "2-digit" })}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </div>

        <p className="font-ui text-caption text-mist text-center">
          Early screening signal only — not a medical diagnosis. Consult a professional.
        </p>
      </div>

      {alert && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-espresso/40 px-6">
          <div className="bg-white rounded-card p-8 max-w-md w-full shadow-subtle">
            <div className="flex items-center gap-3 mb-4">
              <span className="w-3 h-3 rounded-full bg-brick" />
              <h2 className="font-display text-heading-sm text-espresso">
                {alert.notification.title}
              </h2>
            </div>
            <p className="font-ui text-body text-ash mb-6">{alert.notification.body}</p>
            <div className="bg-brick-bg rounded-card p-4 mb-6">
              <p className="font-ui text-caption text-brick mb-1">CRI this session</p>
              <p className="font-display text-heading text-espresso">
                {alert.cognitive_snapshot.cri_session}
              </p>
            </div>
            <div className="flex flex-col gap-3">
              <button className="bg-terracotta text-white rounded-pill py-4 font-ui text-subheading hover:opacity-90 transition">
                📞 Call Ibu Sri now
              </button>
              <button
                onClick={() => setAlert(null)}
                className="border border-sand text-espresso rounded-pill py-4 font-ui text-subheading hover:bg-sand transition"
              >
                View wellness trend
              </button>
            </div>
          </div>
        </div>
      )}
    </main>
  );
}
