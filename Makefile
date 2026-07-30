.DEFAULT_GOAL := help

.PHONY: help test verify up down logs benchmark verify-benchmark

help:
	@awk 'BEGIN {FS = ":.*## "; print "EventForge commands:"} /^[a-zA-Z_-]+:.*## / {printf "  %-18s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

test: ## Run unit tests
	./mvnw -B -ntp test

verify: ## Run unit and Testcontainers integration tests
	./mvnw -B -ntp verify

up: ## Build and start the local stack
	docker compose up --build -d

down: ## Stop the local stack
	docker compose down

logs: ## Follow application logs
	docker compose logs -f eventforge

benchmark: ## Run k6 at RATE events/second (default 20,000)
	docker run --rm --network host \
		-e EVENTFORGE_URL=http://host.docker.internal:8080 \
		-e EVENTFORGE_API_KEY=$${EVENTFORGE_API_KEY:-local-development-key} \
		-e RATE=$${RATE:-20000} -e DURATION=$${DURATION:-60s} \
		-v "$(CURDIR)/benchmarks:/scripts:ro" \
		grafana/k6:latest run /scripts/k6-events.js

verify-benchmark: ## Assert persisted rows contain no duplicate writes
	docker compose exec -T postgres psql -U eventforge -d eventforge \
		-f /dev/stdin < benchmarks/verify-no-duplicates.sql
