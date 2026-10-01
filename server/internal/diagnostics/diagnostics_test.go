package diagnostics

import (
	"bytes"
	"compress/gzip"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"sync"
	"testing"
	"time"
)

const admin = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

func TestAudioCauseAndLifecycleRemainBoundedAndRedacted(t *testing.T) {
	b := batch(19, "audio-batch", "audio-event")
	e := &b.Events[0]
	e.Type = "audio_play_error"
	e.Details = map[string]any{"audioCause": "token=secret " + strings.Repeat("c", 700),
		"audioOperations":  strings.Repeat("prepare,pause,release;", 40),
		"timeoutOperation": float64(1), "audioInstance": float64(2), "password": "secret"}
	if !valid(e) {
		t.Fatal("Audio error rejected")
	}
	cause, ok := e.Details["audioCause"].(string)
	if !ok || len(cause) > 512 || strings.Contains(cause, "secret") || len(cause) < 100 {
		t.Fatal("Cause chain dropped, unbounded, or not redacted")
	}
	if len(e.Details["audioOperations"].(string)) > 512 || e.Details["timeoutOperation"] != float64(1) || e.Details["password"] != nil {
		t.Fatal("Audio operation context invalid")
	}
}

func TestAPKUpdateFieldsRemainBoundedAndRedacted(t *testing.T) {
	b := batch(18, "batch-update", "update-event")
	e := &b.Events[0]
	e.Type = "apk_update"
	e.Details = map[string]any{"stage": "confirm token=private", "status": float64(3), "targetVersion": float64(18), "installedVersion": float64(17), "installSessionID": float64(10), "password": "private", "apkPath": "/private/file"}
	if !valid(e) || e.Details["stage"] != "confirm [redacted]" || e.Details["status"] != float64(3) || len(e.Details) != 5 {
		t.Fatal("APK diagnostic fields missing or unbounded private fields retained")
	}
}

func TestInvalidPublicationDoesNotChangeAllowlist(t *testing.T) {
	s, h, _ := fixture(t)
	release(t, h, 1)
	bad := []Release{{2, "test", strings.Repeat("a", 64), time.Now().UTC().Format(time.RFC3339)}, {3, "test", "invalid", "now"}}
	w := call(h, "admin/diagnostics/releases", "POST", admin, map[string]any{"releases": bad})
	if w.Code != 400 || len(s.latest()) != 1 || s.latest()[0].VersionCode != 1 {
		t.Fatal("invalid publication changed live release authority")
	}
}

func TestReadbackByEventIDIsAuthenticatedAndReadOnly(t *testing.T) {
	s, h, _ := fixture(t)
	release(t, h, 1)
	token := enroll(t, h)
	if call(h, "diagnostics/batches", "POST", token, batch(1, "batch-id", "offline-id")).Code != 200 {
		t.Fatal("ingest failed")
	}
	w := call(h, "admin/diagnostics/summary?eventID=offline-id", "GET", admin, nil)
	var result map[string]any
	json.Unmarshal(w.Body.Bytes(), &result)
	if w.Code != 200 || len(result["samples"].([]any)) != 1 || len(s.state.Events) != 1 {
		t.Fatal("readback failed or mutated state")
	}
	if call(h, "admin/diagnostics/summary?eventID=offline-id", "GET", "", nil).Code != 401 {
		t.Fatal("unauthorized ID query")
	}
}

func fixture(t *testing.T) (*Service, http.Handler, string) {
	t.Helper()
	root := t.TempDir()
	s, e := New(root, admin)
	if e != nil {
		t.Fatal(e)
	}
	m := http.NewServeMux()
	s.Handlers(m)
	return s, m, root
}
func call(h http.Handler, path, method, token string, v any) *httptest.ResponseRecorder {
	b, _ := json.Marshal(v)
	r := httptest.NewRequest(method, "/fengshen-api/v1/"+path, bytes.NewReader(b))
	if token != "" {
		r.Header.Set("Authorization", "Bearer "+token)
	}
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	return w
}
func release(t *testing.T, h http.Handler, v int) {
	t.Helper()
	r := Release{v, "test", strings.Repeat("a", 64), time.Now().UTC().Format(time.RFC3339)}
	w := call(h, "admin/diagnostics/releases", "POST", admin, map[string]any{"releases": []Release{r}})
	if w.Code != 200 {
		t.Fatal(w.Body.String())
	}
}
func enroll(t *testing.T, h http.Handler) string {
	t.Helper()
	w := call(h, "diagnostics/installations", "POST", "", map[string]string{"installationID": "test-install"})
	if w.Code != 201 {
		t.Fatal(w.Body.String())
	}
	var a map[string]string
	json.Unmarshal(w.Body.Bytes(), &a)
	return a["writeToken"]
}
func batch(v int, b, id string) Batch {
	return Batch{BatchID: b, Events: []Event{{SchemaVersion: 1, EventID: id, SessionID: "session", VersionCode: v, VersionName: "test", BuildID: "build", ClientTime: time.Now().UTC().Format(time.RFC3339Nano), Type: "app_start", Severity: "INFO", Device: Device{Emulator: true}}}}
}
func TestRetentionPublicationRestartLateAndConcurrent(t *testing.T) {
	s, h, root := fixture(t)
	tok := enroll(t, h)
	for v := 1; v <= 2; v++ {
		release(t, h, v)
		if w := call(h, "diagnostics/batches", "POST", tok, batch(v, "b"+itoa(v), "e"+itoa(v))); w.Code != 200 {
			t.Fatal(w.Body.String())
		}
	}
	var wg sync.WaitGroup
	wg.Add(2)
	go func() {
		defer wg.Done()
		call(h, "diagnostics/batches", "POST", tok, batch(1, "late-concurrent", "late-event"))
	}()
	go func() { defer wg.Done(); release(t, h, 3) }()
	wg.Wait()
	if s.allowed(1) || !s.allowed(2) || !s.allowed(3) {
		t.Fatal("latest two not enforced")
	}
	for _, e := range s.state.Events {
		if e.VersionCode == 1 {
			t.Fatal("old record survived publication without new reports")
		}
	}
	if w := call(h, "diagnostics/batches", "POST", tok, batch(1, "late", "late")); w.Code != 410 || !strings.Contains(w.Body.String(), "expired_apk_version") {
		t.Fatal("late accepted")
	}
	restarted, e := New(root, admin)
	if e != nil {
		t.Fatal(e)
	}
	if restarted.allowed(1) || len(restarted.state.Events) != 1 {
		t.Fatal("restart retention")
	}
	info, e := os.Stat(filepath.Join(root, "diagnostics.json"))
	if e != nil || (runtime.GOOS != "windows" && info.Mode().Perm() != 0600) {
		t.Fatal("not private")
	}
}
func TestBatchEventDedupAndGzip(t *testing.T) {
	s, h, _ := fixture(t)
	release(t, h, 16)
	tok := enroll(t, h)
	b := batch(16, "batch", "event")
	for i := 0; i < 2; i++ {
		if call(h, "diagnostics/batches", "POST", tok, b).Code != 200 {
			t.Fatal("ingest")
		}
	}
	b.BatchID = "different-batch"
	if call(h, "diagnostics/batches", "POST", tok, b).Code != 200 || len(s.state.Events) != 1 {
		t.Fatal("event ID dedup")
	}
	var z bytes.Buffer
	g := gzip.NewWriter(&z)
	raw, _ := json.Marshal(batch(16, "gzip", "gzip-event"))
	g.Write(raw)
	g.Close()
	r := httptest.NewRequest("POST", "/fengshen-api/v1/diagnostics/batches", &z)
	r.Header.Set("Authorization", "Bearer "+tok)
	r.Header.Set("Content-Encoding", "gzip")
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	if w.Code != 200 || len(s.state.Events) != 2 {
		t.Fatal("compressed upload")
	}
}
func TestAuthenticationRedactionLimitsAndReadOnly(t *testing.T) {
	s, h, _ := fixture(t)
	release(t, h, 16)
	tok := enroll(t, h)
	for _, p := range []string{"admin/diagnostics/summary", "admin/diagnostics/retention", "diagnostics/batches"} {
		method := "GET"
		if strings.Contains(p, "batches") {
			method = "POST"
		}
		if call(h, p, method, "", batch(16, "unauth", "bad")).Code != 401 {
			t.Fatal("anonymous access")
		}
	}
	if call(h, "admin/diagnostics/summary", "GET", tok, nil).Code != 401 {
		t.Fatal("write token can read")
	}
	b := batch(16, "redact", "redact")
	b.Events[0].Stack = "password=secret https://secret.test/?token=abc Bearer abc"
	b.Events[0].Details = map[string]any{"token": "secret", "mapId": 114}
	if call(h, "diagnostics/batches", "POST", tok, b).Code != 200 {
		t.Fatal("redact upload")
	}
	raw, _ := json.Marshal(s.state.Events)
	if strings.Contains(string(raw), "secret") {
		t.Fatal("secret persisted")
	}
	before, _ := os.ReadFile(s.path)
	w := call(h, "admin/diagnostics/summary", "GET", admin, nil)
	after, _ := os.ReadFile(s.path)
	if w.Code != 200 || !bytes.Equal(before, after) {
		t.Fatal("query mutated storage")
	}
	big := batch(16, "big", "big")
	big.Events[0].Stack = strings.Repeat("x", MaxBatch)
	if call(h, "diagnostics/batches", "POST", tok, big).Code != 400 {
		t.Fatal("oversized decompressed payload")
	}
	unknown := batch(17, "unknown", "unknown")
	if call(h, "diagnostics/batches", "POST", tok, unknown).Code != 410 {
		t.Fatal("client created release")
	}
}
func TestCapacityDropsOldLowPriorityAndDoesNotTouchOtherFiles(t *testing.T) {
	s, h, root := fixture(t)
	release(t, h, 16)
	tok := enroll(t, h)
	s.Capacity = 4*1024*1024 + 1200
	sentinel := filepath.Join(root, "player-save-not-diagnostics")
	os.WriteFile(sentinel, []byte("keep"), 0600)
	for i := 0; i < 5; i++ {
		b := batch(16, "b"+itoa(i), "e"+itoa(i))
		b.Events[0].Stack = strings.Repeat("x", 500)
		if call(h, "diagnostics/batches", "POST", tok, b).Code != 200 {
			t.Fatal("capacity upload")
		}
	}
	if s.state.Dropped[16] == 0 {
		t.Fatal("capacity not bounded")
	}
	release(t, h, 17)
	release(t, h, 18)
	if b, e := os.ReadFile(sentinel); e != nil || string(b) != "keep" {
		t.Fatal("other files touched")
	}
}
func TestTestEventsExcludedFromNormalFaults(t *testing.T) {
	_, h, _ := fixture(t)
	release(t, h, 16)
	tok := enroll(t, h)
	b := batch(16, "test-crash", "test-crash")
	b.Events[0].Severity = "FATAL"
	b.Events[0].Test = true
	call(h, "diagnostics/batches", "POST", tok, b)
	w := call(h, "admin/diagnostics/summary", "GET", admin, nil)
	if !strings.Contains(w.Body.String(), `"status":"NO_DATA"`) {
		t.Fatal(w.Body.String())
	}
}
