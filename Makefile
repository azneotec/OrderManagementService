# Runs OrderManagementService on its own: MySQL and Kafka from docker-compose.yml plus the app, no other service.
# DB_*, SERVER_PORT and EUREKA_CLIENT_ENABLED come from the gitignored .env when it exists (see .env.example).
# Without it the defaults in application.properties apply (the compose MySQL on 3307, Kafka on 9092). Spring
# resolves them at runtime, so the jar contains only ${...} placeholders, never the values. The root Makefile
# of the monorepo runs the whole stack instead.
#
#   make up        start MySQL and Kafka, then run the app in the foreground on :8004
#   make down      stop MySQL and Kafka (keeps their data)
#   make docker-up start MySQL, Kafka and the app as a container built from the Dockerfile

SHELL      := /bin/bash
.DEFAULT_GOAL := help

JAR        := target/OrderManagementService-0.0.1-SNAPSHOT.jar
SKIP_TESTS ?= true

# Export .env for one recipe line when present. Sourced by the shell (not `include`) so quoted values work.
WITH_ENV = set -a; [ ! -f ./.env ] || . ./.env; set +a;

.PHONY: help up down jar run test test-integration clean infra-up infra-down infra-reset docker-up docker-down

help: ## Show this help
	@grep -hE '^[a-z-]+:.*## ' $(MAKEFILE_LIST) | awk -F':.*## ' '{printf "  %-17s %s\n", $$1, $$2}'

up: infra-up run ## Start MySQL and Kafka, then build and run the app in the foreground

down: infra-down ## Stop MySQL and Kafka, keeping their data

jar: ## Build the jar (tests skipped by default; use SKIP_TESTS=false to run them)
	@$(WITH_ENV) ./mvnw clean package -DskipTests=$(SKIP_TESTS)
	@echo "Built $(JAR)"

run: ## Run the jar with .env loaded (builds it first if missing; SERVER_PORT=9004 make run starts a second instance)
	@test -f $(JAR) || $(MAKE) --no-print-directory jar
	@$(WITH_ENV) java -jar $(JAR)

test: ## Run the unit and web-layer tests (no database needed)
	@./mvnw test

test-integration: ## Run the integration tests against MySQL on localhost:3307 (make infra-up first)
	@./mvnw test -DexcludedGroups=none -Dgroups=integration

clean: ## Delete target/
	@./mvnw clean

infra-up: ## Start MySQL (host port 3307) and Kafka (9092, UI on 8090) from docker-compose.yml and wait until healthy
	docker compose up -d --wait

infra-down: ## Stop MySQL and Kafka, keeping their data
	docker compose down

infra-reset: ## Stop MySQL and Kafka and delete their data volumes
	docker compose down -v

docker-up: ## Build the image and start MySQL, Kafka and the app container (http://localhost:8004, Eureka off)
	docker compose --profile app up -d --build --wait

docker-down: ## Stop the app container together with MySQL and Kafka, keeping their data
	docker compose --profile app down
