export const DEFAULT_VERSION = '0.1';

export const parseMajorMinor = (version) => {
  const normalized = (version || '').trim() || DEFAULT_VERSION;
  const match = normalized.match(/^(\d+)\.(\d+)(?:\.\d+)?$/);
  if (!match) {
    return { major: 0, minor: 0, valid: false };
  }
  return { major: Number(match[1]), minor: Number(match[2]), valid: true };
};

export const compareVersions = (a, b) => {
  if (a.major !== b.major) return a.major - b.major;
  return a.minor - b.minor;
};

export const nextMajorVersion = (version) => {
  const parsed = parseMajorMinor(version);
  const major = parsed.valid ? parsed.major : 0;
  return `${major + 1}.0`;
};

export const nextMinorVersion = (version) => {
  const parsed = parseMajorMinor(version);
  if (!parsed.valid) {
    return DEFAULT_VERSION;
  }
  return `${parsed.major}.${parsed.minor + 1}`;
};

export const getLatestVersion = (versions, status) => {
  const candidates = versions.filter((item) => !status || item.status === status);
  let best = null;
  candidates.forEach((item) => {
    const parsed = parseMajorMinor(item.value);
    if (!parsed.valid) {
      return;
    }
    if (!best || compareVersions(parsed, best.parsed) > 0) {
      best = { value: item.value, parsed };
    }
  });
  return best ? best.value : '';
};

export const getMaxPublishedMajor = (versions) => {
  let maxMajor = null;
  versions.forEach((item) => {
    if (item.status !== 'published') return;
    const parsed = parseMajorMinor(item.value);
    if (!parsed.valid) return;
    if (maxMajor === null || parsed.major > maxMajor) {
      maxMajor = parsed.major;
    }
  });
  return maxMajor;
};

export const getMaxPublishedMinorForMajor = (versions, major) => {
  let maxMinor = null;
  versions.forEach((item) => {
    if (item.status !== 'published') return;
    const parsed = parseMajorMinor(item.value);
    if (!parsed.valid || parsed.major !== major) return;
    if (maxMinor === null || parsed.minor > maxMinor) {
      maxMinor = parsed.minor;
    }
  });
  return maxMinor;
};

export const getDraftForMajor = (versions, major) => (
  versions.find((item) => {
    if (item.status !== 'draft') return false;
    const parsed = parseMajorMinor(item.value);
    return parsed.valid && parsed.major === major;
  })
);
