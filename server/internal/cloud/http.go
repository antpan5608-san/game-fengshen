package cloud

import (
	"database/sql"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"regexp"
	"strings"
	"sync"
	"time"

	"fengshen-remake/server/internal/diagnostics"
)

var usernamePattern = regexp.MustCompile(`^[a-z0-9][a-z0-9._-]{2,31}$`)

func ValidUsername(value string) bool { return usernamePattern.MatchString(value) }

type failureWindow struct {
	count int
	until time.Time
}
type API struct {
	Store    Store
	mu       sync.Mutex
	failures map[string]failureWindow
}

func NewAPI(store Store) http.Handler {
	return NewAPIWithDiagnostics(store, nil)
}
func NewAPIWithDiagnostics(store Store, d *diagnostics.Service) http.Handler {
	a := &API{Store: store, failures: map[string]failureWindow{}}
	mux := http.NewServeMux()
	if d != nil {
		d.SessionValid = func(r *http.Request) bool {
			t := bearer(r)
			if t == "" {
				return false
			}
			_, e := store.AccountForToken(r.Context(), TokenHash(t))
			return e == nil
		}
		d.Handlers(mux)
	}
	mux.HandleFunc("/fengshen-api/healthz", func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			methodError(w)
			return
		}
		reply(w, 200, map[string]any{"status": "ok"})
	})
	mux.HandleFunc("/fengshen-api/v1/session", a.session)
	mux.HandleFunc("/fengshen-api/v1/save/main", a.save)
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("X-Content-Type-Options", "nosniff")
		mux.ServeHTTP(w, r)
	})
}

func reply(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(value)
}
func reject(w http.ResponseWriter, status int, code string) {
	reply(w, status, map[string]string{"error": code})
}
func methodError(w http.ResponseWriter) { reject(w, http.StatusMethodNotAllowed, "method_not_allowed") }
func decode(w http.ResponseWriter, r *http.Request, dst any) bool {
	r.Body = http.MaxBytesReader(w, r.Body, MaxBodyBytes)
	decoder := json.NewDecoder(r.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(dst); err != nil {
		reject(w, 400, "invalid_json")
		return false
	}
	if err := decoder.Decode(new(any)); !errors.Is(err, io.EOF) {
		reject(w, 400, "invalid_json")
		return false
	}
	return true
}

func (a *API) limited(username string) bool {
	a.mu.Lock()
	defer a.mu.Unlock()
	f := a.failures[username]
	if time.Now().After(f.until) {
		delete(a.failures, username)
		return false
	}
	return f.count >= 5
}
func (a *API) failed(username string) {
	a.mu.Lock()
	defer a.mu.Unlock()
	f := a.failures[username]
	if time.Now().After(f.until) {
		f = failureWindow{until: time.Now().Add(10 * time.Minute)}
	}
	f.count++
	a.failures[username] = f
}
func (a *API) succeeded(username string) { a.mu.Lock(); delete(a.failures, username); a.mu.Unlock() }

func (a *API) session(w http.ResponseWriter, r *http.Request) {
	if r.Method == http.MethodDelete {
		token := bearer(r)
		if token == "" {
			reject(w, 401, "unauthorized")
			return
		}
		if err := a.Store.DeleteSession(r.Context(), TokenHash(token)); err != nil {
			reject(w, 503, "storage_unavailable")
			return
		}
		reply(w, 200, map[string]bool{"loggedOut": true})
		return
	}
	if r.Method != http.MethodPost {
		methodError(w)
		return
	}
	var req struct {
		Username string `json:"username"`
		Password string `json:"password"`
	}
	if !decode(w, r, &req) {
		return
	}
	username := strings.ToLower(strings.TrimSpace(req.Username))
	if !usernamePattern.MatchString(username) || len(req.Password) < 1 || len(req.Password) > 256 {
		reject(w, 400, "invalid_credentials")
		return
	}
	if a.limited(username) {
		reject(w, 429, "too_many_attempts")
		return
	}
	id, hash, err := a.Store.FindPassword(r.Context(), username)
	if err != nil && !errors.Is(err, sql.ErrNoRows) {
		reject(w, 503, "storage_unavailable")
		return
	}
	if err != nil || !VerifyPassword(hash, req.Password) {
		a.failed(username)
		reject(w, 401, "invalid_credentials")
		return
	}
	a.succeeded(username)
	token, err := RandomToken()
	if err != nil {
		reject(w, 500, "random_unavailable")
		return
	}
	expires := time.Now().Add(30 * 24 * time.Hour)
	if err := a.Store.CreateSession(r.Context(), id, TokenHash(token), expires); err != nil {
		reject(w, 503, "storage_unavailable")
		return
	}
	reply(w, 200, map[string]any{"token": token, "username": username, "expiresAt": expires.UTC().Format(time.RFC3339)})
}
func bearer(r *http.Request) string {
	parts := strings.SplitN(r.Header.Get("Authorization"), " ", 2)
	if len(parts) != 2 || parts[0] != "Bearer" || len(parts[1]) < 40 || len(parts[1]) > 128 {
		return ""
	}
	return parts[1]
}
func (a *API) save(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet && r.Method != http.MethodPut {
		methodError(w)
		return
	}
	token := bearer(r)
	if token == "" {
		reject(w, 401, "unauthorized")
		return
	}
	id, err := a.Store.AccountForToken(r.Context(), TokenHash(token))
	if errors.Is(err, sql.ErrNoRows) {
		reject(w, 401, "unauthorized")
		return
	}
	if err != nil {
		reject(w, 503, "storage_unavailable")
		return
	}
	if r.Method == http.MethodGet {
		record, err := a.Store.GetSave(r.Context(), id)
		if errors.Is(err, sql.ErrNoRows) {
			reject(w, 404, "save_not_found")
			return
		}
		if err != nil {
			reject(w, 503, "storage_unavailable")
			return
		}
		reply(w, 200, record)
		return
	}
	var req struct {
		BaseRevision int64    `json:"baseRevision"`
		Snapshot     Snapshot `json:"snapshot"`
	}
	if !decode(w, r, &req) {
		return
	}
	if _, _, err := req.Snapshot.Digest(); err != nil || req.BaseRevision < 0 {
		reject(w, 400, "invalid_snapshot")
		return
	}
	record, err := a.Store.PutSave(r.Context(), id, req.BaseRevision, req.Snapshot)
	if errors.Is(err, ErrConflict) {
		reject(w, 409, "revision_conflict")
		return
	}
	if err != nil {
		reject(w, 503, "storage_unavailable")
		return
	}
	reply(w, 200, record)
}
