import { elderContext } from "./store";
import type { MatchResult } from "./types";

export function findMatch(text: string): MatchResult {
  const lower = text.toLowerCase();
  for (const group of elderContext.community_groups_mock) {
    for (const keyword of group.interest_keywords) {
      if (lower.includes(keyword.toLowerCase())) {
        return {
          matched: true,
          group_id: group.group_id,
          group,
          matched_keyword: keyword,
        };
      }
    }
  }
  return { matched: false };
}
