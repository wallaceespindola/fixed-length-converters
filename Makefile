.DEFAULT_GOAL := help
SKIP_FRONTEND := -Pskip-frontend
SKIP_TESTS := -DskipTests

.PHONY: help build run test benchmark clean lint docs

help: ## Display all available make commands with descriptions
	@echo ""
	@echo "Banking Fixed-Length File Generator & Parser Validation Platform"
	@echo "================================================================="
	@echo ""
	@awk 'BEGIN {FS = ":.*##"; printf "Usage: make \033[36m<target>\033[0m\n\nTargets:\n"} /^[a-zA-Z_-]+:.*?##/ { printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
	@echo ""

build: ## Compile and package the project (skips tests)
	mvn clean package $(SKIP_TESTS)

build-full: ## Compile, package with frontend build
	mvn clean package $(SKIP_TESTS) -P!skip-frontend

run: ## Start the application locally (dev profile, skips frontend build)
	mvn spring-boot:run $(SKIP_FRONTEND) -Dspring-boot.run.profiles=dev

run-prod: ## Start the application without dev profile
	mvn spring-boot:run $(SKIP_FRONTEND)

test: ## Run all test categories (unit + integration, no frontend build)
	mvn verify $(SKIP_FRONTEND)

test-unit: ## Run only unit tests
	mvn test $(SKIP_FRONTEND) -Dtest="*Test" -DfailIfNoTests=false

test-integration: ## Run integration tests
	mvn verify $(SKIP_FRONTEND) -Dtest="*IntegrationTest,*IT" -DfailIfNoTests=false

benchmark: ## Run the JMH benchmark suite
	mvn test $(SKIP_FRONTEND) -Pbenchmark

clean: ## Remove build artifacts and output files
	mvn clean
	rm -rf output/*.txt

lint: ## Run static analysis (compiler warnings + checkstyle if configured)
	mvn compile $(SKIP_FRONTEND) -Xlint:all 2>&1 | grep -E "(warning|error)" | head -50 || true

docs: ## Generate documentation artifacts (JaCoCo report)
	mvn verify $(SKIP_FRONTEND) -DskipTests=false
	@echo "JaCoCo report: target/site/jacoco/index.html"
