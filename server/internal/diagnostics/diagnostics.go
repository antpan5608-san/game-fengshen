// Package diagnostics is isolated from player saves. One private atomic journal,
// one mutex, one server process: publication and ingestion share the same lock.
package diagnostics

import (
	"compress/gzip"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"regexp"
	"runtime"
	"sort"
	"strings"
	"sync"
	"time"
)

const MaxBatch = 256 * 1024
const MaxVersionBytes = 20 * 1024 * 1024

type Release struct {
	VersionCode int    `json:"versionCode"`
	VersionName string `json:"versionName"`
	SHA256      string `json:"sha256"`
	PublishedAt string `json:"publishedAt"`
}
type Device struct {
	Model    string `json:"model"`
	API      int    `json:"api"`
	Android  string `json:"android"`
	Emulator bool   `json:"emulator"`
}
type Event struct {
	SchemaVersion  int            `json:"schemaVersion"`
	EventID        string         `json:"eventID"`
	BatchID        string         `json:"batchID"`
	SessionID      string         `json:"sessionID"`
	VersionCode    int            `json:"versionCode"`
	VersionName    string         `json:"versionName"`
	BuildID        string         `json:"buildID"`
	ContentVersion string         `json:"contentVersion"`
	ContentHash    string         `json:"contentHash"`
	ClientTime     string         `json:"clientTime"`
	MonotonicMs    int64          `json:"monotonicMs"`
	Sequence       int64          `json:"sequence"`
	ReceivedAt     string         `json:"receivedAt"`
	Type           string         `json:"type"`
	Severity       string         `json:"severity"`
	Code           string         `json:"code,omitempty"`
	Stack          string         `json:"stack,omitempty"`
	Test           bool           `json:"test"`
	Device         Device         `json:"device"`
	Details        map[string]any `json:"details,omitempty"`
}
type Batch struct {
	BatchID string  `json:"batchID"`
	Events  []Event `json:"events"`
	Dropped int64   `json:"dropped"`
}
type journal struct {
	Releases        []Release         `json:"releases"`
	Events          []Event           `json:"events"`
	Receipts        map[string]bool   `json:"receipts"`
	EventIDs        map[string]bool   `json:"eventIDs"`
	Installations   map[string]string `json:"installations"`
	Dropped         map[int]int64     `json:"dropped"`
	Cleaned         int64             `json:"cleaned"`
	CleanupFailures int64             `json:"cleanupFailures"`
	UpdatedAt       string            `json:"updatedAt"`
}
type window struct {
	Start time.Time
	Count int
}
type Service struct {
	mu           sync.Mutex
	path, admin  string
	state        journal
	rates        map[string]window
	SessionValid func(*http.Request) bool
	Capacity     int
}

var id = regexp.MustCompile(`^[a-zA-Z0-9._:-]{1,100}$`)
var hash = regexp.MustCompile(`^[a-f0-9]{64}$`)
var sensitive = regexp.MustCompile(`(?i)(https?://\S+|Bearer\s+\S+|(?:password|token|secret|authorization|access.?key)\s*[=:]\s*\S+)`)

func redact(s string, n int) string {
	s = sensitive.ReplaceAllString(s, "[redacted]")
	if len(s) > n {
		s = s[:n]
	}
	return s
}
func New(root, admin string) (*Service, error) {
	if len(admin) < 40 {
		return nil, errors.New("diagnostics admin credential missing")
	}
	if err := os.MkdirAll(root, 0700); err != nil {
		return nil, err
	}
	s := &Service{path: filepath.Join(root, "diagnostics.json"), admin: admin, rates: map[string]window{}, Capacity: MaxVersionBytes}
	s.state = journal{Receipts: map[string]bool{}, Installations: map[string]string{}, Dropped: map[int]int64{}}
	if b, e := os.ReadFile(s.path); e == nil {
		if e = json.Unmarshal(b, &s.state); e != nil {
			return nil, e
		}
	} else if !os.IsNotExist(e) {
		return nil, e
	}
	if s.state.EventIDs == nil {
		s.state.EventIDs = map[string]bool{}
	}
	s.cleanup()
	if err := s.persist(); err != nil {
		return nil, err
	}
	return s, nil
}
func (s *Service) persist() error {
	s.state.UpdatedAt = time.Now().UTC().Format(time.RFC3339Nano)
	b, e := json.Marshal(s.state)
	if e != nil {
		return e
	}
	tmp := s.path + ".pending"
	if e = os.WriteFile(tmp, b, 0600); e != nil {
		return e
	}
	f, e := os.OpenFile(tmp, os.O_RDWR, 0600)
	if e != nil {
		return e
	}
	e = f.Sync()
	f.Close()
	if e != nil {
		return e
	}
	if e = os.Rename(tmp, s.path); e != nil {
		return e
	}
	if runtime.GOOS == "windows" {
		return nil
	} // Directory fsync is unavailable on Windows; production is Linux.
	d, e := os.Open(filepath.Dir(s.path))
	if e == nil {
		e = d.Sync()
		d.Close()
	}
	return e
}
func (s *Service) allowed(v int) bool {
	for _, r := range s.latest() {
		if r.VersionCode == v {
			return true
		}
	}
	return false
}
func (s *Service) latest() []Release {
	r := append([]Release{}, s.state.Releases...)
	sort.Slice(r, func(i, j int) bool { return r[i].VersionCode > r[j].VersionCode })
	if len(r) > 2 {
		r = r[:2]
	}
	return r
}
func (s *Service) cleanup() {
	kept := []Event{}
	for _, e := range s.state.Events {
		if s.allowed(e.VersionCode) {
			kept = append(kept, e)
		} else {
			s.state.Cleaned++
		}
	}
	s.state.Events = kept
	for key := range s.state.Receipts {
		p := strings.SplitN(key, "/", 2)
		keep := false
		for _, r := range s.latest() {
			if p[0] == itoa(r.VersionCode) {
				keep = true
			}
		}
		if !keep {
			delete(s.state.Receipts, key)
		}
	}
	for key := range s.state.EventIDs {
		p := strings.SplitN(key, "/", 2)
		keep := false
		for _, r := range s.latest() {
			if p[0] == itoa(r.VersionCode) {
				keep = true
			}
		}
		if !keep {
			delete(s.state.EventIDs, key)
		}
	}
}
func itoa(v int) string { b, _ := json.Marshal(v); return string(b) }
func send(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	w.WriteHeader(status)
	json.NewEncoder(w).Encode(v)
}
func failure(w http.ResponseWriter, status int, code string) {
	send(w, status, map[string]string{"error": code})
}
func token(r *http.Request) string {
	return strings.TrimPrefix(r.Header.Get("Authorization"), "Bearer ")
}
func (s *Service) isAdmin(r *http.Request) bool {
	return subtle.ConstantTimeCompare([]byte(token(r)), []byte(s.admin)) == 1
}
func tokenHash(t string) string { h := sha256.Sum256([]byte(t)); return hex.EncodeToString(h[:]) }
func (s *Service) rate(key string, max int, span time.Duration) bool {
	now := time.Now()
	if len(s.rates) >= 4096 {
		for k, v := range s.rates {
			if now.Sub(v.Start) > time.Hour {
				delete(s.rates, k)
			}
		}
		if _, exists := s.rates[key]; !exists && len(s.rates) >= 4096 {
			return false
		}
	}
	v := s.rates[key]
	if now.Sub(v.Start) > span {
		v = window{Start: now}
	}
	v.Count++
	s.rates[key] = v
	return v.Count <= max
}
func (s *Service) Handlers(m *http.ServeMux) {
	m.HandleFunc("/fengshen-api/v1/diagnostics/installations", s.install)
	m.HandleFunc("/fengshen-api/v1/diagnostics/batches", s.ingest)
	m.HandleFunc("/fengshen-api/v1/admin/diagnostics/summary", s.summary)
	m.HandleFunc("/fengshen-api/v1/admin/diagnostics/retention", s.retention)
	m.HandleFunc("/fengshen-api/v1/admin/diagnostics/releases", s.release)
}
func decode(r *http.Request, v any) error {
	var source io.Reader = io.LimitReader(r.Body, MaxBatch+1)
	if r.Header.Get("Content-Encoding") == "gzip" {
		z, e := gzip.NewReader(source)
		if e != nil {
			return e
		}
		defer z.Close()
		source = z
	} else if r.Header.Get("Content-Encoding") != "" {
		return errors.New("unsupported encoding")
	}
	b, e := io.ReadAll(io.LimitReader(source, MaxBatch+1))
	if e != nil || len(b) > MaxBatch {
		return errors.New("batch too large")
	}
	d := json.NewDecoder(strings.NewReader(string(b)))
	d.DisallowUnknownFields()
	if e = d.Decode(v); e != nil {
		return e
	}
	if d.Decode(new(any)) != io.EOF {
		return errors.New("trailing data")
	}
	return nil
}
func (s *Service) install(w http.ResponseWriter, r *http.Request) {
	if r.Method != "POST" {
		failure(w, 405, "method_not_allowed")
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	// X-Real-IP is overwritten by the existing trusted loopback nginx route.
	ip := r.Header.Get("X-Real-IP")
	if ip == "" {
		ip = r.RemoteAddr
	}
	if !s.rate("install/"+ip, 6, time.Hour) || len(s.state.Installations) >= 1000 {
		failure(w, 429, "installation_rate_limit")
		return
	}
	var req struct {
		InstallationID string `json:"installationID"`
	}
	if decode(r, &req) != nil || !id.MatchString(req.InstallationID) {
		failure(w, 400, "invalid_installation")
		return
	}
	b := make([]byte, 32)
	if _, e := rand.Read(b); e != nil {
		failure(w, 503, "random_unavailable")
		return
	}
	t := hex.EncodeToString(b)
	s.state.Installations[tokenHash(t)] = req.InstallationID
	if e := s.persist(); e != nil {
		delete(s.state.Installations, tokenHash(t))
		failure(w, 503, "storage_unavailable")
		return
	}
	send(w, 201, map[string]string{"writeToken": t, "scope": "diagnostics:write"})
}
func valid(e *Event) bool {
	if e.SchemaVersion != 1 || !id.MatchString(e.EventID) || !id.MatchString(e.SessionID) || !id.MatchString(e.Type) || !id.MatchString(e.BuildID) || e.VersionCode <= 0 || e.Sequence < 0 {
		return false
	}
	if e.Severity != "INFO" && e.Severity != "WARN" && e.Severity != "ERROR" && e.Severity != "FATAL" {
		return false
	}
	if e.ContentHash != "" && !hash.MatchString(e.ContentHash) {
		return false
	}
	if _, err := time.Parse(time.RFC3339Nano, e.ClientTime); err != nil {
		return false
	}
	e.VersionName = redact(e.VersionName, 100)
	e.ContentVersion = redact(e.ContentVersion, 100)
	e.Stack = redact(e.Stack, 2048)
	e.Code = redact(e.Code, 80)
	e.Device.Model = redact(e.Device.Model, 80)
	e.Device.Android = redact(e.Device.Android, 30)
	allowed := map[string]bool{"mapId": true, "x": true, "y": true, "fromMapId": true, "groupId": true, "battleID": true, "settlementID": true, "experience": true, "money": true, "success": true, "verificationMs": true, "parseMs": true, "atlasMs": true, "audioMs": true, "firstFrameMs": true, "reason": true, "exitTimestamp": true, "trackID": true, "assetID": true, "stackAvailable": true, "stage": true, "targetVersion": true, "installedVersion": true, "installSessionID": true, "status": true, "audioInstance": true, "audioLifecycle": true, "audioOperations": true, "audioThread": true, "audioLooper": true, "audioPlayerLooper": true, "audioForeground": true, "audioClosed": true, "audioCause": true, "timeoutOperation": true}
	for k, v := range e.Details {
		if !allowed[k] {
			delete(e.Details, k)
			continue
		}
		switch n := v.(type) {
		case string:
			limit := 100
			if k == "audioOperations" || k == "audioCause" {
				limit = 512
			}
			e.Details[k] = redact(n, limit)
		case float64, bool:
		default:
			delete(e.Details, k)
		}
	}
	b, _ := json.Marshal(e)
	return len(b) <= 4096
}
func (s *Service) ingest(w http.ResponseWriter, r *http.Request) {
	if r.Method != "POST" {
		failure(w, 405, "method_not_allowed")
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	t := token(r)
	_, installed := s.state.Installations[tokenHash(t)]
	if t == "" || (!installed && (s.SessionValid == nil || !s.SessionValid(r))) {
		failure(w, 401, "unauthorized")
		return
	}
	ip := r.Header.Get("X-Real-IP")
	if ip == "" {
		ip = r.RemoteAddr
	}
	if !s.rate("token/"+tokenHash(t), 60, time.Minute) || !s.rate("ip/"+ip, 120, time.Minute) {
		failure(w, 429, "rate_limit")
		return
	}
	var b Batch
	if decode(r, &b) != nil || !id.MatchString(b.BatchID) || len(b.Events) < 1 || len(b.Events) > 128 || b.Dropped < 0 {
		failure(w, 400, "invalid_batch")
		return
	}
	version := b.Events[0].VersionCode
	if !s.allowed(version) {
		failure(w, 410, "expired_apk_version")
		return
	}
	for i := range b.Events {
		e := &b.Events[i]
		e.BatchID = b.BatchID
		e.ReceivedAt = time.Now().UTC().Format(time.RFC3339Nano)
		if e.VersionCode != version || !valid(e) {
			failure(w, 400, "invalid_event")
			return
		}
	}
	key := itoa(version) + "/" + b.BatchID
	if s.state.Receipts[key] {
		send(w, 200, map[string]any{"duplicate": true, "accepted": 0})
		return
	}
	// Receipt/event IDs remain bounded without forgetting IDs and recounting retries.
	if len(s.state.Receipts) >= 100000 {
		failure(w, 507, "receipt_capacity")
		return
	}
	before, _ := json.Marshal(s.state)
	accepted := 0
	for _, e := range b.Events {
		eventKey := itoa(version) + "/" + e.EventID
		if !s.state.EventIDs[eventKey] {
			s.state.Events = append(s.state.Events, e)
			s.state.EventIDs[eventKey] = true
			accepted++
		}
	}
	s.state.Receipts[key] = true
	indexBytes, _ := json.Marshal([]any{s.state.Receipts, s.state.EventIDs})
	if len(indexBytes) > 4*1024*1024 {
		json.Unmarshal(before, &s.state)
		failure(w, 507, "receipt_capacity")
		return
	}
	s.state.Dropped[version] += b.Dropped
	for {
		events := []Event{}
		for _, e := range s.state.Events {
			if e.VersionCode == version {
				events = append(events, e)
			}
		}
		raw, _ := json.Marshal(events)
		if len(raw) <= s.Capacity-4*1024*1024 {
			break
		}
		index := -1
		for i, e := range s.state.Events {
			if e.VersionCode == version && (e.Severity == "INFO" || e.Severity == "WARN") {
				index = i
				break
			}
		}
		if index < 0 {
			for i, e := range s.state.Events {
				if e.VersionCode == version {
					index = i
					break
				}
			}
		}
		if index < 0 {
			break
		}
		s.state.Events = append(s.state.Events[:index], s.state.Events[index+1:]...)
		s.state.Dropped[version]++
	}
	if e := s.persist(); e != nil {
		json.Unmarshal(before, &s.state)
		failure(w, 503, "storage_unavailable")
		return
	}
	send(w, 200, map[string]any{"accepted": accepted, "batchID": b.BatchID})
}
func (s *Service) release(w http.ResponseWriter, r *http.Request) {
	if !s.isAdmin(r) {
		failure(w, 401, "unauthorized")
		return
	}
	if r.Method != "POST" {
		failure(w, 405, "method_not_allowed")
		return
	}
	var req struct {
		Releases []Release `json:"releases"`
	}
	if decode(r, &req) != nil || len(req.Releases) < 1 || len(req.Releases) > 2 {
		failure(w, 400, "invalid_release")
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	before, _ := json.Marshal(s.state)
	// Validate the complete authoritative update before changing the in-memory allowlist.
	for _, v := range req.Releases {
		if v.VersionCode < 1 || !hash.MatchString(v.SHA256) || v.PublishedAt == "" {
			failure(w, 400, "invalid_release")
			return
		}
		for _, old := range s.state.Releases {
			if old.VersionCode == v.VersionCode {
				if old.SHA256 != v.SHA256 {
					failure(w, 409, "release_hash_conflict")
					return
				}
			}
		}
		for _, other := range req.Releases {
			if other.VersionCode == v.VersionCode && other.SHA256 != v.SHA256 {
				failure(w, 409, "release_hash_conflict")
				return
			}
		}
	}
	for _, v := range req.Releases {
		exists := false
		for _, old := range s.state.Releases {
			if old.VersionCode == v.VersionCode {
				exists = true
			}
		}
		if !exists {
			s.state.Releases = append(s.state.Releases, v)
		}
	}
	s.cleanup()
	if e := s.persist(); e != nil {
		json.Unmarshal(before, &s.state)
		s.state.CleanupFailures++
		failure(w, 503, "cleanup_failed_retry_required")
		return
	}
	send(w, 200, s.retentionData())
}
func (s *Service) retentionData() map[string]any {
	versions := map[int]int{}
	for _, e := range s.state.Events {
		versions[e.VersionCode]++
	}
	return map[string]any{"allowedReleases": s.latest(), "storedVersionCounts": versions, "cleanedEvents": s.state.Cleaned, "cleanupFailures": s.state.CleanupFailures, "droppedByVersion": s.state.Dropped, "perVersionCapacityBytes": s.Capacity, "privateStorage": true, "receiptCount": len(s.state.Receipts), "releaseAuthority": "admin_verified_OSS_publication_metadata"}
}
func (s *Service) retention(w http.ResponseWriter, r *http.Request) {
	if !s.isAdmin(r) {
		failure(w, 401, "unauthorized")
		return
	}
	if r.Method != "GET" {
		failure(w, 405, "method_not_allowed")
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	send(w, 200, s.retentionData())
}
func (s *Service) summary(w http.ResponseWriter, r *http.Request) {
	if !s.isAdmin(r) {
		failure(w, 401, "unauthorized")
		return
	}
	if r.Method != "GET" {
		failure(w, 405, "method_not_allowed")
		return
	}
	queryID := r.URL.Query().Get("eventID")
	if queryID != "" && !id.MatchString(queryID) {
		failure(w, 400, "invalid_event_id")
		return
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	sessions := map[string]bool{}
	emulators := map[string]bool{}
	real := map[string]bool{}
	tests := 0
	last := ""
	first := ""
	contents := map[string]string{}
	errorsByType := map[string]map[string]any{}
	samples := []Event{}
	for _, e := range s.state.Events {
		if first == "" || e.ReceivedAt < first {
			first = e.ReceivedAt
		}
		if e.ReceivedAt > last {
			last = e.ReceivedAt
		}
		contents[e.ContentVersion] = e.ContentHash
		if e.Test {
			tests++
			continue
		}
		sessions[e.SessionID] = true
		if e.Device.Emulator {
			emulators[e.SessionID] = true
		} else {
			real[e.SessionID] = true
		}
		// An observed historical ANR/crash remains a diagnostic finding even when
		// Android cannot tell us which APK version originally produced it.
		if e.Severity == "ERROR" || e.Severity == "FATAL" || (e.Type == "historical_process_exit" && e.Severity == "WARN") {
			key := e.Type + "/" + e.Code
			x := errorsByType[key]
			if x == nil {
				x = map[string]any{"count": 0}
				errorsByType[key] = x
			}
			x["count"] = x["count"].(int) + 1
			x["latestAt"] = e.ReceivedAt
			x["sample"] = e
		}
	}
	start := len(s.state.Events) - 30
	if start < 0 {
		start = 0
	}
	samples = append(samples, s.state.Events[start:]...)
	if queryID != "" {
		samples = []Event{}
		for _, e := range s.state.Events {
			if e.EventID == queryID {
				samples = append(samples, e)
			}
		}
	}
	status := "NO_ISSUES_OBSERVED"
	if len(sessions) == 0 {
		status = "NO_DATA"
	} else if len(errorsByType) > 0 {
		status = "ISSUES_FOUND"
	}
	send(w, 200, map[string]any{"status": status, "queriedAt": time.Now().UTC().Format(time.RFC3339Nano), "environment": "fengshen-remake-cloud", "queryRange": map[string]string{"firstReceivedAt": first, "lastReceivedAt": last}, "retention": s.retentionData(), "lastReportedAt": last, "eventCount": len(s.state.Events), "sessionCount": len(sessions), "emulatorSessions": len(emulators), "realDeviceSessions": len(real), "testEventCount": tests, "contents": contents, "errors": errorsByType, "samples": samples, "coverageLimits": []string{"Only uploaded instrumented sessions; no real samples does not prove real-device health", "Test events excluded from normal fault/session counts", "Exit reasons may lack stack; user force-stop is not classified as crash"}})
}
