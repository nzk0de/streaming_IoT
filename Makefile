# --- Variables ---
.DEFAULT_GOAL := help
SHELL := /bin/bash

# Project and Namespace Configuration
NAMESPACE      ?= streaming-kafka
HELM_RELEASE   ?= streaming-poc
HELM_CHART_PATH ?= ./k8s-helm

# AWS ECR Configuration
AWS_ACCOUNT_ID := <REPLACE_WITH_AWS_ACCOUNT_ID>
REGION         := eu-central-1
REPO_URI       := $(AWS_ACCOUNT_ID).dkr.ecr.$(REGION).amazonaws.com

# Application Configuration
APPS := fastapi-app mqtt-bridge flink-app kafka-connect-simple

# --- Docker & ECR ---
.PHONY: build create-ecr-repos push

build: ## Build all custom application Docker images
	@for app in $(APPS); do \
		echo "🚧 Building $$app..."; \
		docker build -t $$app:latest -f ./dockers/$$app/Dockerfile .; \
		docker tag $$app:latest $(REPO_URI)/$$app:latest; \
	done

create-ecr-repos: ## Create ECR repositories if they don't exist
	@for app in $(APPS); do \
		echo "📦 Creating ECR repository for $$app..."; \
		aws ecr create-repository --repository-name $$app --region $(REGION) 2>/dev/null || echo "🔸 Repository $$app already exists."; \
	done

push: build ## Build images and push them to AWS ECR
	@echo "🔐 Authenticating with ECR..."
	@aws ecr get-login-password --region $(REGION) | docker login --username AWS --password-stdin $(REPO_URI)
	@for app in $(APPS); do \
		echo "🚀 Pushing $$app to $(REPO_URI)..."; \
		docker push $(REPO_URI)/$$app:latest; \
	done

# --- Kubernetes & Deployment ---
.PHONY: deploy install-operators helm-install helm-uninstall restart-pods status minikube-start check-pods setup-ingress setup-loadbalancer show-access

deploy: install-operators ## Deploy full application using Helm
	@echo "🚀 Creating namespace if it doesn't exist..."
	@kubectl create namespace $(NAMESPACE) --dry-run=client -o yaml | kubectl apply -f -
	@echo "🚀 Deploying application using Helm..."
	@helm upgrade --install $(HELM_RELEASE) ./k8s-helm --namespace $(NAMESPACE) --wait --timeout=10m
	@echo "✅ Application deployed successfully!"

install-operators: ## Install/Update prerequisite operators (Strimzi, Flink, External Secrets)
	@echo "✅ Ensuring namespace '$(NAMESPACE)' exists..."
	@kubectl create namespace $(NAMESPACE) 2>/dev/null || echo "🔸 Namespace '$(NAMESPACE)' already exists."
	
	@echo "✅ Installing/Updating Strimzi Operator..."
	@kubectl apply -f https://strimzi.io/install/latest?namespace=$(NAMESPACE) -n $(NAMESPACE)

	@echo "✅ Installing/Updating cert-manager..."
	@kubectl apply -f https://github.com/jetstack/cert-manager/releases/download/v1.8.2/cert-manager.yaml

	@echo "⏳ Waiting for cert-manager deployments to be ready..."
	@kubectl wait --namespace cert-manager \
		--for=condition=Available \
		--timeout=180s \
		deployment/cert-manager \
		deployment/cert-manager-webhook \
		deployment/cert-manager-cainjector

	@echo "✅ Installing/Updating External Secrets Operator..."
	@helm repo add external-secrets https://charts.external-secrets.io 2>/dev/null || echo "🔸 External Secrets repo already exists."
	@helm repo update external-secrets
	@helm upgrade --install external-secrets external-secrets/external-secrets --namespace external-secrets-system --create-namespace

	@echo "⏳ Waiting for External Secrets Operator to be ready..."
	@kubectl wait --namespace external-secrets-system \
		--for=condition=Available \
		--timeout=180s \
		deployment/external-secrets \
		deployment/external-secrets-webhook \
		deployment/external-secrets-cert-controller

	@echo "✅ Installing/Updating Flink Operator..."
	@helm repo add flink-operator-repo https://downloads.apache.org/flink/flink-kubernetes-operator-1.12.1 2>/dev/null || echo "🔸 Flink repo already exists."
	@helm repo update flink-operator-repo
	@helm upgrade --install flink-kubernetes-operator flink-operator-repo/flink-kubernetes-operator --namespace $(NAMESPACE) --version 1.12.1

	@echo "✅ Installing/Updating NGINX Ingress Controller..."
	@helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx 2>/dev/null || echo "🔸 NGINX Ingress repo already exists."
	@helm repo update ingress-nginx
	@helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx --namespace ingress-nginx --create-namespace --set controller.service.type=LoadBalancer

	@echo "⚠️  Note: Make sure eso-aws-creds secret exists in $(NAMESPACE) namespace"
	@echo "    Run 'kubectl create secret generic eso-aws-creds --from-literal=accessKeyID=<your-key> --from-literal=secretAccessKey=<your-secret> -n $(NAMESPACE)'"

helm-install: ## Deploy the application stack using Helm (use 'deploy' for full workflow)
	@echo "🚀 Deploying Helm release '$(HELM_RELEASE)'..."
	@helm upgrade --install $(HELM_RELEASE) $(HELM_CHART_PATH) \
		--namespace $(NAMESPACE) \
		--create-namespace

helm-uninstall: ## Uninstall the application Helm release
	@echo "🗑️ Uninstalling Helm release '$(HELM_RELEASE)' from namespace '$(NAMESPACE)'..."
	@helm uninstall $(HELM_RELEASE) -n $(NAMESPACE) 2>/dev/null || echo "⚠️ Helm release not found."

restart-pods: ## Restart deployments to pick up new ECR credentials
	@echo "🔄 Restarting deployments to pick up fresh ECR credentials..."
	@kubectl rollout restart deployment -n $(NAMESPACE) --selector="app.kubernetes.io/instance=$(HELM_RELEASE)" 2>/dev/null || true
	@echo "⏳ Waiting for rollout to complete..."
	@kubectl rollout status deployment -n $(NAMESPACE) --selector="app.kubernetes.io/instance=$(HELM_RELEASE)" --timeout=300s 2>/dev/null || true
	@echo "✅ Pod restart complete."

status: ## Show deployment status
	@echo "📋 External Secret status:"
	@kubectl get externalsecret -n $(NAMESPACE) 2>/dev/null || echo "No external secrets found"
	@echo "📋 Pod status:"
	@kubectl get pods -n $(NAMESPACE) 2>/dev/null || echo "No pods found"

minikube-start: ## Start the minikube cluster with recommended resources
	minikube start --cpus=12 --memory=20000


check-pods: ## Watch the status of all pods in the namespace in real-time
	@echo "🔎 Pod status in namespace '$(NAMESPACE)' (Press Ctrl+C to stop):"
	@kubectl get pods -n $(NAMESPACE) -w

show-access: ## Show how to access your applications (ingress or loadbalancer IPs)
	@echo "🌐 Application Access Information:"
	@echo ""
	@echo "📋 Checking Ingress Controller status..."
	@kubectl get svc -n ingress-nginx ingress-nginx-controller 2>/dev/null || echo "⚠️  NGINX Ingress not found"
	@echo ""
	@echo "📋 LoadBalancer services (if enabled):"
	@kubectl get svc -n $(NAMESPACE) --selector="app.kubernetes.io/instance=$(HELM_RELEASE)" -o wide 2>/dev/null || echo "No LoadBalancer services found"
	@echo ""
	@echo "📋 Ingress resources:"
	@kubectl get ingress -n $(NAMESPACE) 2>/dev/null || echo "No ingress resources found"
	@echo ""
	@echo "🔗 If using ingress with local domains, add these to your /etc/hosts:"
	@echo "   <INGRESS_IP> fastapi.streaming-poc.local"
	@echo "   <INGRESS_IP> kafka-ui.streaming-poc.local"  
	@echo "   <INGRESS_IP> flink-ui.streaming-poc.local"
	@echo "   <INGRESS_IP> mqtt-ui.streaming-poc.local"

# --- Help ---
.PHONY: help
help:
	@echo ""
	@echo "  Usage: make <target>"
	@echo ""
	@echo "  \033[1mAWS ECR Image Management:\033[0m"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		grep -E '(build|create-ecr-repos|push):' | \
		sort | awk 'BEGIN {FS = ":.*?## "}; {printf "    \033[36m%-20s\033[0m %s\n", $$1, $$2}'
	@echo ""
	@echo "  \033[1mKubernetes Deployment:\033[0m"
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		grep -E '(deploy|install-operators|helm-install|helm-uninstall|restart-pods|status|minikube-start|check-pods|show-access):' | \
		sort | awk 'BEGIN {FS = ":.*?## "}; {printf "    \033[36m%-20s\033[0m %s\n", $$1, $$2}'
	@echo ""


port-forward: ## Forward ports for MQTT UI, Kafka UI, and Flink UI
	@echo "🔌 Forwarding ports... Press Ctrl+C to stop all."
	@echo "   - MQTT UI:   localhost:5721"
	@echo "   - Kafka UI:  localhost:9080" 
	@echo "   - Flink UI:  localhost:8081"
	@echo "   - FastAPI:    localhost:8000"
	@kubectl port-forward svc/$(HELM_RELEASE)-mqtt-ui 5721:5721 -n $(NAMESPACE) --address 0.0.0.0 & \
	kubectl port-forward svc/$(HELM_RELEASE)-kafka-ui 9080:8080 -n $(NAMESPACE) --address 0.0.0.0 & \
	kubectl port-forward svc/$(HELM_RELEASE)-flink-app-java-rest 8081:8081 -n $(NAMESPACE) --address 0.0.0.0 & \
	kubectl port-forward svc/$(HELM_RELEASE)-fastapi 8000:8000 -n $(NAMESPACE) --address 0.0.0.0 & \
	wait