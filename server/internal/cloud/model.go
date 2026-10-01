package cloud

import (
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"

	"golang.org/x/crypto/argon2"
)

const MaxBodyBytes = 64 * 1024

type CharacterState struct {
	ID         string          `json:"id"`
	Level      int             `json:"level"`
	Experience int             `json:"experience"`
	HP         int             `json:"hp"`
	MaxHP      int             `json:"maxHp"`
	MP         int             `json:"mp"`
	Strength   int             `json:"strength"`
	Stamina    int             `json:"stamina"`
	Agility    int             `json:"agility"`
	Spirit     int             `json:"spirit"`
	MaxMP      *int            `json:"maxMp,omitempty"`
	Equipment  *EquipmentState `json:"equipment,omitempty"`
}

// Optional for saves written before the opening equipment evidence was added.
type EquipmentState struct {
	RightHand int `json:"rightHand"`
	LeftHand  int `json:"leftHand"`
	Body      int `json:"body"`
	Feet      int `json:"feet"`
}

// Only current executable player state belongs in a save. ROM assets and map data do not.
type Snapshot struct {
	SaveSchemaVersion int              `json:"saveSchemaVersion"`
	ContentVersion    string           `json:"contentVersion"`
	MapID             int              `json:"mapId"`
	X                 int              `json:"x"`
	Y                 int              `json:"y"`
	Direction         string           `json:"direction"`
	Characters        []CharacterState `json:"characters"`
	Inventory         map[string]int   `json:"inventory"`
	Flags             map[string]bool  `json:"flags"`
	Money             int              `json:"money"`
	EncounterSteps    int              `json:"encounterSteps,omitempty"`
}

func (s Snapshot) Validate() error {
	if s.SaveSchemaVersion != 1 || len(s.ContentVersion) < 1 || len(s.ContentVersion) > 96 ||
		strings.ContainsAny(s.ContentVersion, "\r\n\x00") {
		return errors.New("unsupported save schema or content version")
	}
	if s.MapID < 0 || s.MapID > 65535 || s.X < 0 || s.X > 65535 || s.Y < 0 || s.Y > 65535 || s.X%16 != 8 || s.Y%16 != 8 {
		return errors.New("invalid map or position")
	}
	switch s.Direction {
	case "UP", "DOWN", "LEFT", "RIGHT":
	default:
		return errors.New("invalid direction")
	}
	if len(s.Characters) < 1 || len(s.Characters) > 4 {
		return errors.New("invalid character count")
	}
	seen := map[string]bool{}
	for _, c := range s.Characters {
		if len(c.ID) < 1 || len(c.ID) > 64 || seen[c.ID] || c.Level < 1 || c.Level > 99 ||
			c.Experience < 0 || c.Experience > 0xffffff || c.MaxHP < 1 || c.MaxHP > 9999 ||
			c.HP < 0 || c.HP > c.MaxHP || c.MP < 0 || c.MP > 9999 ||
			c.Strength < 0 || c.Strength > 9999 || c.Stamina < 0 || c.Stamina > 9999 ||
			c.Agility < 0 || c.Agility > 9999 || c.Spirit < 0 || c.Spirit > 9999 {
			return errors.New("invalid character state")
		}
		if c.MaxMP != nil && (*c.MaxMP < c.MP || *c.MaxMP > 9999) {
			return errors.New("invalid maximum MP")
		}
		if c.Equipment != nil {
			for _, slot := range []int{c.Equipment.RightHand, c.Equipment.LeftHand, c.Equipment.Body, c.Equipment.Feet} {
				if slot < -1 || slot > 255 {
					return errors.New("invalid equipment slot")
				}
			}
		}
		seen[c.ID] = true
	}
	if len(s.Inventory) > 256 || len(s.Flags) > 1024 || s.Money < 0 || s.Money > 9999999 || s.EncounterSteps < 0 || s.EncounterSteps > 255 {
		return errors.New("save collection too large")
	}
	for id, n := range s.Inventory {
		if len(id) < 1 || len(id) > 96 || n < 0 || n > 9999 {
			return errors.New("invalid inventory")
		}
	}
	for id := range s.Flags {
		if len(id) < 1 || len(id) > 96 {
			return errors.New("invalid flag")
		}
	}
	return nil
}

func (s Snapshot) Digest() (string, []byte, error) {
	if err := s.Validate(); err != nil {
		return "", nil, err
	}
	data, err := json.Marshal(s)
	if err != nil {
		return "", nil, err
	}
	hash := sha256.Sum256(data)
	return hex.EncodeToString(hash[:]), data, nil
}

func RandomToken() (string, error) {
	bytes := make([]byte, 32)
	if _, err := rand.Read(bytes); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(bytes), nil
}

func TokenHash(token string) string {
	hash := sha256.Sum256([]byte(token))
	return hex.EncodeToString(hash[:])
}

func HashPassword(password string) (string, error) {
	if len(password) < 12 || len(password) > 256 {
		return "", errors.New("password must be 12..256 bytes")
	}
	salt := make([]byte, 16)
	if _, err := rand.Read(salt); err != nil {
		return "", err
	}
	digest := argon2.IDKey([]byte(password), salt, 2, 19*1024, 1, 32)
	return fmt.Sprintf("argon2id$2$19456$1$%s$%s", base64.RawStdEncoding.EncodeToString(salt), base64.RawStdEncoding.EncodeToString(digest)), nil
}

func VerifyPassword(encoded, password string) bool {
	parts := strings.Split(encoded, "$")
	if len(parts) != 6 || parts[0] != "argon2id" || parts[1] != "2" || parts[2] != "19456" || parts[3] != "1" {
		return false
	}
	salt, err := base64.RawStdEncoding.DecodeString(parts[4])
	if err != nil || len(salt) != 16 {
		return false
	}
	expected, err := base64.RawStdEncoding.DecodeString(parts[5])
	if err != nil || len(expected) != 32 {
		return false
	}
	if len(password) > 256 {
		return false
	}
	actual := argon2.IDKey([]byte(password), salt, 2, 19*1024, 1, 32)
	return subtle.ConstantTimeCompare(expected, actual) == 1
}
