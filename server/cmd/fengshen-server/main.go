package main

import (
	"context"
	"flag"
	"io"
	"log"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"fengshen-remake/server/internal/cloud"
	"fengshen-remake/server/internal/diagnostics"
)

func main() {
	create := flag.String("create-account", "", "create one Fengshen account; read password from stdin")
	reset := flag.String("reset-account", "", "reset password and revoke sessions; read password from stdin")
	flag.Parse()
	if *create != "" && *reset != "" {
		log.Fatal("choose one account operation")
	}
	store, err := cloud.OpenPostgres(os.Getenv("FENGSHEN_DATABASE_URL"))
	if err != nil {
		log.Fatal("Fengshen database unavailable: ", err)
	}
	defer store.DB.Close()
	if *create != "" || *reset != "" {
		username := *create
		if *reset != "" {
			username = *reset
		}
		username = strings.ToLower(strings.TrimSpace(username))
		if !cloud.ValidUsername(username) {
			log.Fatal("invalid username")
		}
		password, err := io.ReadAll(io.LimitReader(os.Stdin, 300))
		if err != nil {
			log.Fatal(err)
		}
		value := strings.TrimSuffix(strings.TrimSuffix(string(password), "\n"), "\r")
		if err := store.CreateAccount(context.Background(), username, value, *reset != ""); err != nil {
			log.Fatal("account operation failed: ", err)
		}
		log.Print("Fengshen account operation completed for ", username)
		return
	}
	addr := os.Getenv("FENGSHEN_LISTEN")
	if addr == "" {
		addr = "127.0.0.1:8092"
	}
	var diag *diagnostics.Service
	if root := os.Getenv("FENGSHEN_DIAGNOSTICS_DIR"); root != "" {
		diag, err = diagnostics.New(root, os.Getenv("FENGSHEN_DIAGNOSTICS_ADMIN"))
		if err != nil {
			log.Fatal("Private diagnostic store unavailable")
		}
	}
	server := &http.Server{Addr: addr, Handler: cloud.NewAPIWithDiagnostics(store, diag), ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout: 15 * time.Second, WriteTimeout: 20 * time.Second, IdleTimeout: 30 * time.Second}
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()
	go func() {
		<-ctx.Done()
		shutdown, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = server.Shutdown(shutdown)
	}()
	log.Print("Fengshen cloud save listening on ", addr)
	if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		log.Fatal(err)
	}
}
