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


/** Independent publication decision; the diagnostic report is never downgraded.
 * Accept only explicitly reviewed prior-version interruption records. A new or
 * unknown error still blocks, as do stale/unavailable/untrusted inspection data.
 */
export function assessRuntimePublication(report, accepted, now = Date.now()) {
  const reject = reason => ({allowed: false, status: 'BLOCKED', reason});
  if (!object(report) || !object(report.result) || !object(accepted) ||
      accepted.schemaVersion !== 1 || accepted.taskId !== report.task_id ||
      accepted.authorization !== 'docs/history/world-full01-stage-publication.md' ||
      !Array.isArray(accepted.issues) || !['preflight', 'postflight'].includes(report.stage))
    return reject('invalid_release_inspection_or_authorization');
  const at = Date.parse(report.queriedAt);
  if (!Number.isFinite(at) || !Number.isFinite(now) || at > now + 60000 || now - at > 7 * 86400000)
    return reject('stale_or_invalid_inspection');
  const result = report.result;
  const retention = result.retention;
  const releases = retention?.allowedReleases;
  if (!object(retention) || retention.cleanupFailures !== 0 ||
      retention.releaseAuthority !== 'admin_verified_OSS_publication_metadata' ||
      !Array.isArray(releases) || releases.length < 1 || releases.length > 2 ||
      releases.some(r => !Number.isSafeInteger(r.versionCode) || r.versionCode < 1 ||
        !/^[a-f0-9]{64}$/.test(r.sha256 || '')) ||
      new Set(releases.map(r => r.versionCode)).size !== releases.length)
    return reject('untrusted_release_retention_or_cleanup_failure');
  if (!['NO_DATA', 'NO_ISSUES_OBSERVED', 'ISSUES_FOUND'].includes(result.status))
    return reject('inspection_unavailable_or_unknown_status');
  if (result.errors !== undefined && !object(result.errors)) return reject('invalid_error_buckets');
  const errors = Object.entries(result.errors || {});
  if (errors.some(([, e]) => !object(e) || !Number.isSafeInteger(e.count) || e.count < 1))
    return reject('invalid_error_counts');
  if (!errors.length) {
    if (result.status === 'ISSUES_FOUND') return reject('issues_without_classification');
    return {allowed: true, status: 'ALLOW', diagnosticStatus: result.status, acknowledgedIssues: []};
  }
  if (result.status !== 'ISSUES_FOUND') return reject('errors_with_inconsistent_diagnostic_status');
  const latest = Math.max(...releases.map(r => r.versionCode));
  const matched = [];
  for (const [bucket, error] of errors) {
    const issue = accepted.issues.find(i => object(i) &&
      i.classification === 'NON_BLOCKING_PRIOR_VERSION_DOWNLOAD_INTERRUPTION' &&
      i.evidence === accepted.authorization && i.rootCause === 'UNCONFIRMED' &&
      i.bucket === 'apk_update/ProtocolException' && bucket === i.bucket &&
      i.type === 'apk_update' && error.type === i.type && i.code === 'ProtocolException' && error.code === i.code &&
      i.stage === 'download_or_verify' && error.stage === i.stage && error.severity === 'ERROR' &&
      Number.isSafeInteger(i.maxCount) && i.maxCount === 1 && error.count <= i.maxCount &&
      i.latestAt === error.latestAt && Number.isFinite(Date.parse(i.latestAt)) &&
      i.versionCode === error.versionCode && error.versionCode < latest &&
      i.recoveredVersionCode === latest &&
      releases.some(r => r.versionCode === i.versionCode && r.sha256 === i.releaseSha256) &&
      releases.some(r => r.versionCode === i.recoveredVersionCode && r.sha256 === i.recoveredReleaseSha256));
    if (!issue) return reject('unresolved_new_unknown_or_blocking_error');
    matched.push({bucket, count: error.count, latestAt: error.latestAt, versionCode: error.versionCode,
      classification: issue.classification, recoveryEvidence: issue.evidence, rootCause: issue.rootCause});
  }
  return {allowed: true, status: 'ALLOW_WITH_KNOWN_NON_BLOCKING_ISSUES',
    diagnosticStatus: result.status, acknowledgedIssues: matched};
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    if (process.argv[2] === '--release-assessment') {
      if (process.argv.length !== 5) throw Error('Expected report and reviewed issue file');
      const read = path => JSON.parse(readFileSync(path, 'utf8').replace(/^\uFEFF/, ''));
      const decision = assessRuntimePublication(read(process.argv[3]), read(process.argv[4]));
      process.stdout.write(JSON.stringify(decision) + '\n');
      if (!decision.allowed) process.exitCode = 2;
    } else {
      const input = JSON.parse(readFileSync(0, 'utf8').replace(/^\uFEFF/, ''));
      process.stdout.write(JSON.stringify(publicRuntimeSummary(input, process.argv.includes('--summary-only'))) + '\n');
    }
  } catch {
    // Never echo malformed input or parser excerpts containing private data.
    console.error('Runtime summary projection failed');
    process.exitCode = 1;
  }
}
