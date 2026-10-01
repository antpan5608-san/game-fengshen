package cloud

import (
	"bytes"
	"context"
	"database/sql"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

func sample() Snapshot {
	return Snapshot{SaveSchemaVersion: 1, ContentVersion: "opening-to-world-b1", MapID: 114, X: 136, Y: 344, Direction: "DOWN",
		Characters: []CharacterState{{ID: "nezha", Level: 1, Experience: 0, HP: 20, MaxHP: 20, MP: 0, Strength: 8, Stamina: 4, Agility: 2, Spirit: 4}},
		Inventory:  map[string]int{}, Flags: map[string]bool{}}
}

func TestAndroidEncounterStepsCompatibility(t *testing.T) {
	token := "isolated-save-session-for-json-compatibility-test"
	f := &fakeStore{session: TokenHash(token)}
	h := NewAPI(f)
	raw, _ := json.Marshal(sample())
	var snapshot map[string]any
	json.Unmarshal(raw, &snapshot)
	snapshot["contentVersion"] = "opening-segment-001-c9"
	snapshot["encounterSteps"] = 17 // Existing Android SaveSnapshot.json() field.
	w := request(h, "PUT", "/fengshen-api/v1/save/main", token, map[string]any{"baseRevision": 0, "snapshot": snapshot})
	if w.Code != 200 {
		t.Fatalf("Current Android save rejected: %d %s", w.Code, w.Body.String())
	}
	read := request(h, "GET", "/fengshen-api/v1/save/main", token, nil)
	var restored map[string]any
	json.Unmarshal(read.Body.Bytes(), &restored)
	if restored["snapshot"].(map[string]any)["encounterSteps"] != float64(17) {
		t.Fatal("Encounter counter lost on restore")
	}
	for _, bad := range []int{-1, 256} {
		snapshot["encounterSteps"] = bad
		if request(h, "PUT", "/fengshen-api/v1/save/main", token, map[string]any{"baseRevision": 1, "snapshot": snapshot}).Code != 400 {
			t.Fatal("Invalid counter accepted")
		}
	}
	delete(snapshot, "encounterSteps") // Legacy snapshots remain valid and default to zero.
	snapshot["inventedBattleField"] = true
	if request(h, "PUT", "/fengshen-api/v1/save/main", token, map[string]any{"baseRevision": 1, "snapshot": snapshot}).Code != 400 {
		t.Fatal("Strict unknown-field validation weakened")
	}
	delete(snapshot, "inventedBattleField")
	if request(h, "PUT", "/fengshen-api/v1/save/main", token, map[string]any{"baseRevision": 1, "snapshot": snapshot}).Code != 200 {
		t.Fatal("Legacy snapshot rejected")
	}
}

func TestPasswordAndSnapshotValidation(t *testing.T) {
	hash, err := HashPassword("correct horse battery staple")
	if err != nil {
		t.Fatal(err)
	}
	if !VerifyPassword(hash, "correct horse battery staple") || VerifyPassword(hash, "wrong password") {
		t.Fatal("password verification broken")
	}
	a := sample()
	h, raw, err := a.Digest()
	if err != nil || len(h) != 64 || len(raw) == 0 {
		t.Fatal(err)
	}
	a.Characters[0].HP = 21
	if err := a.Validate(); err == nil {
		t.Fatal("HP > maxHP accepted")
	}
	a = sample()
	badMax := -1
	a.Characters[0].MaxMP = &badMax
	if err := a.Validate(); err == nil {
		t.Fatal("negative max MP accepted")
	}
	a.Characters[0].MaxMP = nil
	a.Characters[0].Equipment = &EquipmentState{RightHand: 256, LeftHand: -1, Body: 0, Feet: 28}
	if err := a.Validate(); err == nil {
		t.Fatal("out-of-range equipment accepted")
	}
}

type fakeStore struct {
	record   SaveRecord
	hasSave  bool
	password string
	session  string
}

func (f *fakeStore) FindPassword(_ context.Context, username string) (string, string, error) {
	if username != "antpan" {
		return "", "", sql.ErrNoRows
	}
	return "account", f.password, nil
}
func (f *fakeStore) CreateSession(_ context.Context, _ string, tokenHash string, _ time.Time) error {
	f.session = tokenHash
	return nil
}
func (f *fakeStore) AccountForToken(_ context.Context, tokenHash string) (string, error) {
	if tokenHash != f.session {
		return "", sql.ErrNoRows
	}
	return "account", nil
}
func (f *fakeStore) DeleteSession(_ context.Context, tokenHash string) error {
	if f.session == tokenHash {
		f.session = ""
	}
	return nil
}
func (f *fakeStore) GetSave(_ context.Context, _ string) (SaveRecord, error) {
	if !f.hasSave {
		return SaveRecord{}, sql.ErrNoRows
	}
	return f.record, nil
}
func (f *fakeStore) PutSave(_ context.Context, _ string, base int64, s Snapshot) (SaveRecord, error) {
	if (!f.hasSave && base != 0) || (f.hasSave && base != f.record.Revision) {
		return SaveRecord{}, ErrConflict
	}
	h, _, err := s.Digest()
	if err != nil {
		return SaveRecord{}, err
	}
	f.record = SaveRecord{Revision: base + 1, Snapshot: s, SHA256: h, UpdatedAt: time.Now()}
	f.hasSave = true
	return f.record, nil
}
func request(h http.Handler, method, path, token string, body any) *httptest.ResponseRecorder {
	var data []byte
	if body != nil {
		data, _ = json.Marshal(body)
	}
	r := httptest.NewRequest(method, path, bytes.NewReader(data))
	if token != "" {
		r.Header.Set("Authorization", "Bearer "+token)
	}
	w := httptest.NewRecorder()
	h.ServeHTTP(w, r)
	return w
}
func TestLoginCloudRestoreAndRevisionConflict(t *testing.T) {
	hash, _ := HashPassword("a long test password 123")
	f := &fakeStore{password: hash}
	h := NewAPI(f)
	if w := request(h, "GET", "/fengshen-api/v1/save/main", "", nil); w.Code != 401 {
		t.Fatalf("anonymous save read: %d", w.Code)
	}
	if w := request(h, "POST", "/fengshen-api/v1/session", "", map[string]string{"username": "antpan", "password": "bad"}); w.Code != 401 {
		t.Fatalf("bad login: %d", w.Code)
	}
	w := request(h, "POST", "/fengshen-api/v1/session", "", map[string]string{"username": "antpan", "password": "a long test password 123"})
	if w.Code != 200 {
		t.Fatalf("login: %d %s", w.Code, w.Body.String())
	}
	var login struct {
		Token string `json:"token"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &login); err != nil || login.Token == "" {
		t.Fatal("missing token")
	}
	if w := request(h, "GET", "/fengshen-api/v1/save/main", login.Token, nil); w.Code != 404 {
		t.Fatalf("unexpected save: %d", w.Code)
	}
	initial := sample()
	initial.Money = 100
	initial.Flags = map[string]bool{"rom.npc.114.1": true}
	initial.Inventory = map[string]int{"reference.item.1": 1}
	zero := 0
	initial.Characters[0].MaxMP = &zero
	initial.Characters[0].Equipment = &EquipmentState{RightHand: 0, LeftHand: -1, Body: 0, Feet: 28}
	w = request(h, "PUT", "/fengshen-api/v1/save/main", login.Token, map[string]any{"baseRevision": 0, "snapshot": initial})
	if w.Code != 200 {
		t.Fatalf("save: %d %s", w.Code, w.Body.String())
	}
	initial.MapID = 16
	initial.X = 3256
	initial.Y = 2280
	initial.Characters[0].Equipment.RightHand = -1
	if w := request(h, "PUT", "/fengshen-api/v1/save/main", login.Token, map[string]any{"baseRevision": 0, "snapshot": initial}); w.Code != 409 {
		t.Fatalf("stale save overwrote: %d", w.Code)
	}
	w = request(h, "PUT", "/fengshen-api/v1/save/main", login.Token, map[string]any{"baseRevision": 1, "snapshot": initial})
	if w.Code != 200 {
		t.Fatalf("revision update: %d", w.Code)
	}
	w = request(h, "GET", "/fengshen-api/v1/save/main", login.Token, nil)
	var saved SaveRecord
	if err := json.Unmarshal(w.Body.Bytes(), &saved); err != nil || saved.Revision != 2 || saved.Snapshot.MapID != 16 ||
		saved.Snapshot.Money != 100 || !saved.Snapshot.Flags["rom.npc.114.1"] || saved.Snapshot.Inventory["reference.item.1"] != 1 ||
		saved.Snapshot.Characters[0].MaxMP == nil || *saved.Snapshot.Characters[0].MaxMP != 0 ||
		saved.Snapshot.Characters[0].Equipment == nil || saved.Snapshot.Characters[0].Equipment.RightHand != -1 {
		t.Fatalf("cloud restore wrong: %s", w.Body.String())
	}
	if w := request(h, "DELETE", "/fengshen-api/v1/session", login.Token, nil); w.Code != 200 {
		t.Fatalf("logout: %d", w.Code)
	}
	if w := request(h, "GET", "/fengshen-api/v1/save/main", login.Token, nil); w.Code != 401 {
		t.Fatal("logged-out token still valid")
	}
}
