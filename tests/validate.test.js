import test from 'node:test';
import assert from 'node:assert/strict';
import { validateChatRequest } from '../server/validate.js';

test('accepts a valid conversation ending with a user message', () => {
  // Arrange
  const body = {
    messages: [
      { role: 'user', text: 'Hello' },
      { role: 'model', text: 'Hi there!' },
      { role: 'user', text: 'I had soup for lunch.' },
    ],
  };

  // Act
  const result = validateChatRequest(body);

  // Assert
  assert.equal(result.ok, true);
  assert.equal(result.messages.length, 3);
});

test('trims whitespace from message text', () => {
  const result = validateChatRequest({ messages: [{ role: 'user', text: '  hi  ' }] });
  assert.equal(result.ok, true);
  assert.equal(result.messages[0].text, 'hi');
});

test('rejects a missing or empty messages array', () => {
  assert.equal(validateChatRequest({}).ok, false);
  assert.equal(validateChatRequest({ messages: [] }).ok, false);
  assert.equal(validateChatRequest(null).ok, false);
  assert.equal(validateChatRequest('hello').ok, false);
});

test('rejects invalid roles', () => {
  const result = validateChatRequest({ messages: [{ role: 'system', text: 'x' }] });
  assert.equal(result.ok, false);
});

test('rejects empty or non-string text', () => {
  assert.equal(validateChatRequest({ messages: [{ role: 'user', text: '   ' }] }).ok, false);
  assert.equal(validateChatRequest({ messages: [{ role: 'user', text: 42 }] }).ok, false);
});

test('rejects when the last message is not from the user', () => {
  const result = validateChatRequest({
    messages: [
      { role: 'user', text: 'Hello' },
      { role: 'model', text: 'Hi!' },
    ],
  });
  assert.equal(result.ok, false);
});

test('rejects oversized message text', () => {
  const result = validateChatRequest({
    messages: [{ role: 'user', text: 'a'.repeat(4001) }],
  });
  assert.equal(result.ok, false);
});

test('rejects conversations with too many messages', () => {
  const messages = Array.from({ length: 41 }, (_, index) => ({
    role: index % 2 === 0 ? 'user' : 'model',
    text: 'hello',
  }));
  const result = validateChatRequest({ messages });
  assert.equal(result.ok, false);
});
