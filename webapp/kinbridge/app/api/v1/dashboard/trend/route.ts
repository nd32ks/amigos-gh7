import { NextResponse } from "next/server";
import { getTrend } from "@/lib/store";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const days = parseInt(searchParams.get("days") || "30", 10);
    return NextResponse.json({ points: getTrend(days) });
  } catch (err) {
    console.error("/api/v1/dashboard/trend error:", err);
    return NextResponse.json({ error: "Failed to load trend" }, { status: 500 });
  }
}
