"use client";

import Link from "next/link";
import { useState } from "react";

const caseload = [
  {
    id: "elder_0001",
    name: "Sri Rahayu Wijaya",
    area: "Gading Serpong",
    ewma: 64,
    adherence: 92,
    last_chat: "2 jam lalu",
    alert: true,
  },
  {
    id: "elder_0002",
    name: "Yanto Suharto",
    area: "Sleman, Yogyakarta",
    ewma: 78,
    adherence: 85,
    last_chat: "Kemarin",
    alert: false,
  },
  {
    id: "elder_0003",
    name: "Tini Marlina",
    area: "Bantul, Yogyakarta",
    ewma: 81,
    adherence: 96,
    last_chat: "Pagi ini",
    alert: false,
  },
];

export default function CarePage() {
  const [notes, setNotes] = useState<Record<string, string[]>>({});
  const [draft, setDraft] = useState("");
  const [active, setActive] = useState<string | null>(null);

  function addNote(elderId: string) {
    if (!draft.trim()) return;
    setNotes((prev) => ({ ...prev, [elderId]: [...(prev[elderId] || []), draft] }));
    setDraft("");
    setActive(null);
  }

  return (
    <main className="min-h-screen bg-cream px-6 py-8">
      <div className="max-w-4xl mx-auto">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-8 gap-4">
          <Link href="/" className="font-display text-heading text-espresso">
            Panel Perawat — Sari
          </Link>
          <span className="font-ui text-body text-ash">3 elders under care</span>
        </div>

        <div className="bg-amber-bg border border-amber rounded-card px-6 py-4 mb-6">
          <p className="font-ui text-body text-espresso">
            You see derived wellness signals and care directives only. No diary or raw health records.
          </p>
        </div>

        <div className="space-y-4">
          {caseload.map((elder) => (
            <div key={elder.id} className="bg-white border border-sand rounded-card p-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 mb-4">
                <div>
                  <div className="flex items-center gap-3">
                    <h2 className="font-display text-heading-sm text-espresso">{elder.name}</h2>
                    {elder.alert && <span className="w-3 h-3 rounded-full bg-brick" />}
                  </div>
                  <p className="font-ui text-body text-ash">
                    {elder.area} · Last chat {elder.last_chat}
                  </p>
                </div>
                <div className="flex gap-6">
                  <div className="text-right">
                    <p className="font-ui text-caption text-mist">EWMA</p>
                    <p className="font-display text-heading text-espresso">{elder.ewma}</p>
                  </div>
                  <div className="text-right">
                    <p className="font-ui text-caption text-mist">Adherence</p>
                    <p className="font-display text-heading text-espresso">{elder.adherence}%</p>
                  </div>
                </div>
              </div>

              <div className="h-2 bg-sand rounded-pill overflow-hidden mb-4">
                <div
                  className="h-full bg-sage rounded-pill"
                  style={{ width: `${elder.adherence}%` }}
                />
              </div>

              <button
                onClick={() => setActive(active === elder.id ? null : elder.id)}
                className="font-ui text-body text-terracotta underline underline-offset-4"
              >
                Tulis catatan kunjungan
              </button>

              {active === elder.id && (
                <div className="mt-4">
                  <textarea
                    value={draft}
                    onChange={(e) => setDraft(e.target.value)}
                    placeholder="Catatan kunjungan…"
                    className="w-full bg-cream border border-sand rounded-card p-4 font-ui text-body text-espresso focus:outline-none focus:ring-2 focus:ring-terracotta"
                    rows={3}
                  />
                  <button
                    onClick={() => addNote(elder.id)}
                    className="mt-2 bg-terracotta text-white rounded-pill px-6 py-2 font-ui text-body"
                  >
                    Simpan
                  </button>
                </div>
              )}

              {notes[elder.id]?.length > 0 && (
                <div className="mt-4 space-y-2">
                  {notes[elder.id].map((note, i) => (
                    <div key={i} className="bg-sand/50 rounded-card p-3 font-ui text-body text-espresso">
                      {note}
                    </div>
                  ))}
                </div>
              )}
            </div>
          ))}
        </div>
      </div>
    </main>
  );
}
