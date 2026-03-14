export function extractFrontmatterId(markdown) {
  if (!markdown) {
    return null;
  }
  const normalized = markdown.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  const lines = normalized.split('\n');
  if (lines.length === 0 || lines[0].trim() !== '---') {
    return null;
  }
  let endIndex = -1;
  for (let i = 1; i < lines.length; i += 1) {
    if (lines[i].trim() === '---') {
      endIndex = i;
      break;
    }
  }
  if (endIndex === -1) {
    return null;
  }
  for (let i = 1; i < endIndex; i += 1) {
    const line = lines[i];
    const separatorIndex = line.indexOf(':');
    if (separatorIndex === -1) {
      continue;
    }
    const key = line.slice(0, separatorIndex).trim();
    if (key !== 'id') {
      continue;
    }
    return line.slice(separatorIndex + 1).trim().replace(/^"|"$/g, '');
  }
  return null;
}
