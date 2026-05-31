.DEFAULT_GOAL := help

.PHONY: help build run test test-unit test-integration benchmark clean kill lint docs

help: ## Display all available make commands with descriptions
	@echo ""
	@echo "Banking Fixed-Length File Generator & Parser Validation Platform"
	@echo "================================================================="
	@echo ""
	@awk 'BEGIN {FS = ":.*##"; printf "Usage: make \033[36m<target>\033[0m\n\nTargets:\n"} /^[a-zA-Z_-]+:.*?##/ { printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
	@echo ""

build: ## Compile, run all tests and install (full pipeline)
	mvn clean install

run: ## Start the application locally (Swagger UI at /swagger-ui.html)
	mvn spring-boot:run

test: ## Run all tests with coverage (unit + integration + JaCoCo)
	mvn verify

test-unit: ## Run only unit tests
	mvn test -Dtest="*Test" -DfailIfNoTests=false

test-integration: ## Run integration tests
	mvn verify -Dtest="*IntegrationTest,*IT" -DfailIfNoTests=false

benchmark: ## Run the JMH benchmark suite
	mvn test -Pbenchmark

kill: ## Kill Java processes to free port 8080
	@echo "Killing Java processes..."
	@pkill -f 'java.*FixedLengthConvertersApplication' 2>/dev/null && echo "  Spring Boot stopped" || echo "  No Spring Boot process found"
	@pkill -f 'java.*spring-boot:run' 2>/dev/null && echo "  Maven spring-boot:run stopped" || true
	@echo "Done. Port 8080 released."

clean: ## Remove build artifacts and output files
	mvn clean
	rm -rf output/*.txt

lint: ## Run static analysis (compiler warnings)
	mvn compile -Xlint:all 2>&1 | grep -E "(warning|error)" | head -50 || true

docs: ## Generate JaCoCo coverage report
	mvn verify
	@echo "JaCoCo report: target/site/jacoco/index.html"
