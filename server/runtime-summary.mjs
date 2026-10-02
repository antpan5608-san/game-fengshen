// Public report projection only. The protected query and its fault/release gates
// stay unchanged; individual diagnostic events never enter reports or CI logs.
import {readFileSync} from 'node:fs';
import {pathToFileURL} from 'node:url';

function object(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}
function pick(value, keys) {
  return Object.fromEntries(keys.filter(key => Object.hasOwn(value || {}, key))
    .filter(key => ['string', 'number', 'boolean'].includes(typeof value[key]))
    .map(key => [key, value[key]]));
}
function counts(value) {
  return Object.fromEntries(Object.entries(value || {}).filter(([key, count]) =>
    /^\d+$/.test(key) && Number.isSafeInteger(count) && count >= 0));
}

export function publicRuntimeSummary(input, summaryOnly = false) {
  if (!object(input) || typeof input.status !== 'string') throw Error('Invalid runtime summary');
  const result = pick(input, ['status', 'reason', 'httpStatus', 'queriedAt', 'environment',
    'lastReportedAt', 'eventCount', 'sessionCount', 'emulatorSessions',
    'realDeviceSessions', 'testEventCount']);
  if (input.queryRange) result.queryRange = pick(input.queryRange, ['firstReceivedAt', 'lastReceivedAt']);
  if (Array.isArray(input.coverageLimits)) result.coverageLimits = input.coverageLimits.filter(x => typeof x === 'string');
  if (Object.hasOwn(input, 'errors')) {
    if (!object(input.errors)) throw Error('Invalid runtime errors');
    result.errors = Object.fromEntries(Object.entries(input.errors).map(([key, error]) => {
      if (!object(error) || !Number.isSafeInteger(error.count) || error.count < 0) throw Error('Invalid runtime error count');
      // Keep every error bucket and its count. Project only classification from
      // the sample, never the sample itself or arbitrary details/stack strings.
      const entry = pick(error, ['count', 'latestAt']);
      const sample = error.sample || {};
      Object.assign(entry, pick(sample, ['type', 'code', 'severity', 'versionCode', 'versionName']));
      Object.assign(entry, pick(sample.details, ['stage', 'targetVersion', 'installedVersion']));
      if (typeof sample.device?.emulator === 'boolean') entry.emulator = sample.device.emulator;
      return [key, entry];
    }));
  }
  if (object(input.retention)) {
    const retention = pick(input.retention, ['cleanupFailures', 'releaseAuthority']);
    if (Array.isArray(input.retention.allowedReleases)) retention.allowedReleases = input.retention.allowedReleases
      .map(release => pick(release, ['versionCode', 'versionName', 'sha256', 'publishedAt']));
    if (input.retention.storedVersionCounts) retention.storedVersionCounts = counts(input.retention.storedVersionCounts);
    if (!summaryOnly) {
      Object.assign(retention, pick(input.retention, ['cleanedEvents', 'perVersionCapacityBytes', 'privateStorage', 'receiptCount']));
      if (input.retention.droppedByVersion) retention.droppedByVersion = counts(input.retention.droppedByVersion);
    }
    result.retention = retention;
  }
  if (!summaryOnly && object(input.contents)) result.contents = Object.fromEntries(
    Object.entries(input.contents).filter(([, hash]) => typeof hash === 'string' && (hash === '' || /^[a-f0-9]{64}$/.test(hash))));
  return result;
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const input = JSON.parse(readFileSync(0, 'utf8').replace(/^\uFEFF/, ''));
    process.stdout.write(JSON.stringify(publicRuntimeSummary(input, process.argv.includes('--summary-only'))) + '\n');
  } catch {
    // Never echo malformed input or parser excerpts containing private data.
    console.error('Runtime summary projection failed');
    process.exitCode = 1;
  }
}
