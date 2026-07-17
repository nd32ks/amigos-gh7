const MAX_MESSAGES = 40;
const MAX_TEXT_LENGTH = 4000;
const VALID_ROLES = new Set(['user', 'model']);

/**
 * Validates a chat request body of shape { messages: [{ role, text }] }.
 * Returns { ok: true, messages } with a sanitized copy, or { ok: false, error }.
 */
export function validateChatRequest(body) {
  if (typeof body !== 'object' || body === null) {
    return { ok: false, error: 'Request body must be a JSON object.' };
  }
  const { messages } = body;
  if (!Array.isArray(messages) || messages.length === 0) {
    return { ok: false, error: '"messages" must be a non-empty array.' };
  }
  if (messages.length > MAX_MESSAGES) {
    return { ok: false, error: `"messages" cannot exceed ${MAX_MESSAGES} items.` };
  }

  const sanitized = [];
  for (const message of messages) {
    if (typeof message !== 'object' || message === null) {
      return { ok: false, error: 'Each message must be an object.' };
    }
    if (!VALID_ROLES.has(message.role)) {
      return { ok: false, error: 'Each message role must be "user" or "model".' };
    }
    if (typeof message.text !== 'string' || message.text.trim().length === 0) {
      return { ok: false, error: 'Each message must have non-empty text.' };
    }
    if (message.text.length > MAX_TEXT_LENGTH) {
      return { ok: false, error: `Message text cannot exceed ${MAX_TEXT_LENGTH} characters.` };
    }
    sanitized.push({ role: message.role, text: message.text.trim() });
  }

  if (sanitized[sanitized.length - 1].role !== 'user') {
    return { ok: false, error: 'The last message must be from the user.' };
  }
  return { ok: true, messages: sanitized };
}
