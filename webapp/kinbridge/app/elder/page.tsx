"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";

type OrbState = "idle" | "listening" | "speaking";

interface TurnResponse {
  session_id: string;
  companion: { text: string; probe_fact_id?: string };
  escalation?: "acute" | "warning" | "silent" | "none";
  companion_directive?: string;
  match?: {
    matched: boolean;
    group?: { name: string; distance_km: number; meets: string };
  };
}

export default function ElderPage() {
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [companionText, setCompanionText] = useState<string>("");
  const [orbState, setOrbState] = useState<OrbState>("idle");
  const [pivot, setPivot] = useState(false);
  const [input, setInput] = useState("");
  const [showInput, setShowInput] = useState(false);
  const [match, setMatch] = useState<null | {
    name: string;
    distance_km: number;
    meets: string;
  }>(null);
  const [greetingClicks, setGreetingClicks] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  const hour = new Date().getHours();
  const timeGreeting =
    hour < 11 ? "Selamat pagi" : hour < 15 ? "Selamat siang" : hour < 19 ? "Selamat sore" : "Selamat malam";

  async function startSession() {
    const res = await fetch("/api/v1/turn", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ role: "companion" }),
    });
    const data: TurnResponse = await res.json();
    setSessionId(data.session_id);
    setCompanionText(data.companion.text);
    setOrbState("speaking");
    setTimeout(() => setOrbState("idle"), 2000);
  }

  useEffect(() => {
    startSession();
  }, []);

  async function sendElderTurn(text: string) {
    if (!sessionId) return;
    setOrbState("listening");
    const res = await fetch("/api/v1/turn", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ role: "elder", session_id: sessionId, text }),
    });
    const data: TurnResponse = await res.json();
    setOrbState("speaking");

    if (data.companion_directive === "CALM_REASSURANCE_PIVOT") {
      setPivot(true);
    }
    if (data.match?.matched && data.match.group) {
      setMatch(data.match.group);
      setTimeout(() => setMatch(null), 12000);
    }
    setCompanionText(data.companion.text);
    setTimeout(() => setOrbState("idle"), 2000);
  }

  async function triggerDemo() {
    const scenarios = ["t1_miss", "t2_warning", "match_found"] as const;
    const idx = greetingClicks % scenarios.length;
    setGreetingClicks((c) => c + 1);
    const res = await fetch("/api/v1/demo/trigger", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ scenario: scenarios[idx], session_id: sessionId }),
    });
    const json = await res.json();
    const data: TurnResponse = json.result;
    if (data.companion_directive === "CALM_REASSURANCE_PIVOT") {
      setPivot(true);
    }
    if (data.match?.matched && data.match.group) {
      setMatch(data.match.group);
      setTimeout(() => setMatch(null), 12000);
    }
    setCompanionText(data.companion.text);
    setOrbState("speaking");
    setTimeout(() => setOrbState("idle"), 2500);
  }

  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      if (e.shiftKey && e.key.toLowerCase() === "d") {
        e.preventDefault();
        triggerDemo();
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionId, greetingClicks]);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!input.trim()) return;
    sendElderTurn(input.trim());
    setInput("");
  }

  function handleGreetingClick() {
    setGreetingClicks((c) => c + 1);
    if (greetingClicks + 1 >= 3) {
      setShowInput(true);
      setTimeout(() => inputRef.current?.focus(), 100);
    }
  }

  const orbClasses = {
    idle: "bg-sand scale-100",
    listening: "bg-terracotta animate-pulse scale-110",
    speaking: "bg-sage animate-[breathe_3s_ease-in-out_infinite] scale-105",
  };

  return (
    <main
      className={`min-h-screen flex flex-col items-center px-6 py-10 transition-colors duration-[2000ms] ${
        pivot ? "bg-[#F3E9DA]" : "bg-cream"
      }`}
    >
      <div className="w-full max-w-md flex items-center justify-between mb-10">
        <Link href="/" className="font-ui text-caption text-mist uppercase tracking-wide">
          ← Kembali
        </Link>
        <div className="font-ui text-caption text-mist">Elder</div>
      </div>

      <div className="text-center mb-12">
        <h1
          className="font-display text-heading-lg text-espresso mb-2 cursor-pointer select-none"
          onClick={handleGreetingClick}
        >
          {timeGreeting}, Ibu Sri
        </h1>
        <p className="font-ui text-body text-ash">Ketuk lingkaran untuk ngobrol</p>
      </div>

      <button
        onClick={() => setOrbState((s) => (s === "idle" ? "listening" : "idle"))}
        className={`w-48 h-48 rounded-full flex items-center justify-center transition-all duration-300 mb-12 ${orbClasses[orbState]}`}
        aria-label="Mic orb"
      >
        <span className="font-ui text-subheading text-espresso/80">
          {orbState === "listening" ? "Mendengar..." : orbState === "speaking" ? "Kenang..." : "Tap"}
        </span>
      </button>

      <div className="w-full max-w-md text-center mb-10">
        <p className="font-display text-elder text-espresso min-h-[120px]">
          {companionText || "…"}
        </p>
      </div>

      {showInput && (
        <form onSubmit={handleSubmit} className="w-full max-w-md flex gap-2 mb-6">
          <input
            ref={inputRef}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            placeholder="Ketik jawaban Ibu…"
            className="flex-1 bg-white border border-sand rounded-input px-4 py-3 font-ui text-body text-espresso placeholder:text-mist focus:outline-none focus:ring-2 focus:ring-terracotta"
          />
          <button
            type="submit"
            className="bg-terracotta text-white rounded-pill px-6 py-3 font-ui text-body hover:opacity-90 transition"
          >
            Kirim
          </button>
        </form>
      )}

      <button
        onClick={() => setShowInput((s) => !s)}
        className="font-ui text-body text-ash underline underline-offset-4 mb-12"
      >
        {showInput ? "Sembunyikan keyboard" : "Ketik jawaban"}
      </button>

      <button className="min-h-tap px-8 py-4 border border-sand rounded-pill font-ui text-subheading text-espresso hover:bg-sand transition">
        ⏸ Berhenti sebentar
      </button>

      {match && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-espresso/30 px-6">
          <div className="bg-cream rounded-lg-card p-8 max-w-sm w-full text-center shadow-subtle">
            <h2 className="font-display text-heading text-espresso mb-4">
              Ada teman baru untuk Ibu!
            </h2>
            <div className="bg-sand rounded-card p-4 mb-6 text-left">
              <p className="font-ui text-subheading text-espresso">{match.name}</p>
              <p className="font-ui text-body text-ash">{match.distance_km} km</p>
              <p className="font-ui text-body text-ash">{match.meets}</p>
            </div>
            <button
              onClick={() => setMatch(null)}
              className="w-full bg-terracotta text-white rounded-pill py-4 font-ui text-subheading"
            >
              Beritahu Dewi
            </button>
          </div>
        </div>
      )}

      <p className="fixed bottom-4 left-0 right-0 text-center font-ui text-caption text-mist">
        Shift + D untuk demo
      </p>
    </main>
  );
}
