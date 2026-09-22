function cleanText(value, maxLength) {
  if (typeof value !== 'string') {
    return '';
  }
  return value.trim().substring(0, maxLength);
}

export function parseIncoming(value) {
  if (typeof value === 'object' && value !== null) {
    return value;
  }
  if (typeof value !== 'string') {
    return null;
  }
  try {
    return JSON.parse(value);
  } catch (error) {
    return null;
  }
}

export function normalizeRecords(value, maxItems) {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter(function (item) {
    return item && typeof item.eventId === 'string';
  }).slice(0, maxItems);
}

export function mergeIncoming(records, incoming, maxItems) {
  if (!incoming || String(incoming.schema) !== '1') {
    return normalizeRecords(records, maxItems);
  }
  var eventId = String(incoming.eventId || '');
  if (!eventId) {
    return normalizeRecords(records, maxItems);
  }
  var result = normalizeRecords(records, maxItems).slice();
  var existingIndex = -1;
  for (var i = 0; i < result.length; i++) {
    if (result[i].eventId === eventId) {
      existingIndex = i;
      break;
    }
  }
  var item = existingIndex >= 0 ? result.splice(existingIndex, 1)[0] : {
    eventId: eventId,
    title: '课堂问题',
    question: '',
    fastAnswer: '',
    fastModel: '',
    strongAnswer: '',
    strongModel: '',
    updatedAt: 0
  };
  var kind = cleanText(incoming.kind, 16);
  if (kind === 'cue') {
    item.title = cleanText(incoming.title, 80) || item.title;
    item.question = cleanText(incoming.question, 1500) || item.question;
  } else if (kind === 'answer') {
    var tier = cleanText(incoming.tier, 16);
    if (tier.indexOf('强') >= 0) {
      item.strongAnswer = cleanText(incoming.answer, 3500);
      item.strongModel = cleanText(incoming.model, 80);
    } else {
      item.fastAnswer = cleanText(incoming.answer, 3500);
      item.fastModel = cleanText(incoming.model, 80);
    }
  }
  item.updatedAt = Number(incoming.sentAt) || Date.now();
  result.unshift(item);
  return result.slice(0, maxItems);
}
