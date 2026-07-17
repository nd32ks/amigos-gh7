import { NextResponse } from "next/server";
import { getEvents } from "@/lib/store";

export const dynamic = "force-dynamic";

export async function GET(request: Request) {
  try {
    const { searchParams } = new URL(request.url);
    const limit = parseInt(searchParams.get("limit") || "50", 10);
    return NextResponse.json({ events: getEvents(limit) });
  } catch (err) {
    console.error("/api/v1/dashboard/events error:", err);
    return NextResponse.json({ error: "Failed to load events" }, { status: 500 });
  }
}
