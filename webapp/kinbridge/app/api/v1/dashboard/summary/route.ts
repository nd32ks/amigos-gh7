import { NextResponse } from "next/server";
import { getSummary } from "@/lib/store";

export async function GET() {
  try {
    return NextResponse.json(getSummary());
  } catch (err) {
    console.error("/api/v1/dashboard/summary error:", err);
    return NextResponse.json({ error: "Failed to load summary" }, { status: 500 });
  }
}
