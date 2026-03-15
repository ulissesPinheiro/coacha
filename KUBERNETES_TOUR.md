# 🚢 Kubernetes Tour — Resumão

> Sessão de estudo DevOps | App: **coacha** (Spring Boot 4.0 / Java 21)

---

## 🏗️ Arquitetura do Cluster

```
┌──────────────────────────────── CLUSTER ──────────────────────────────────┐
│  ┌─────────── Control Plane ───────────┐                                  │
│  │  API Server  ← kubectl fala aqui   │                                   │
│  │  Scheduler   ← decide onde rodar   │                                   │
│  │  etcd        ← banco de estado     │                                   │
│  │  Controller  ← garante replicas    │                                   │
│  └─────────────────────────────────────┘                                  │
│                                                                            │
│  ┌──── Worker Node (minikube) ────────────────────────────────────────┐   │
│  │  kubelet | kube-proxy                                              │   │
│  │  [Pod coacha] [Pod coacha] [Pod coacha]                            │   │
│  └────────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 📦 Objetos praticados

### Namespace
Isolamento lógico dentro do cluster.
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: coacha-app
```
```bash
kubectl get namespaces
kubectl get all -n coacha-app
```

---

### Deployment
Gerencia réplicas, self-healing e rolling updates.
```yaml
apiVersion: apps/v1
kind: Deployment
spec:
  replicas: 1
  template:
    spec:
      containers:
        - name: coacha
          image: coacha:4.0
          imagePullPolicy: Never   # imagem local no Minikube
          ports:
            - containerPort: 8080
          envFrom:
            - configMapRef:
                name: coacha-config
            - secretRef:
                name: coacha-secrets
          livenessProbe:           # reinicia se falhar
            httpGet:
              path: /students
              port: 8080
          readinessProbe:          # só recebe tráfego quando passar
            httpGet:
              path: /students
              port: 8080
          resources:
            requests: { cpu: 100m, memory: 128Mi }
            limits:   { cpu: 500m, memory: 256Mi }
```
```bash
kubectl get deployments -n coacha-app
kubectl rollout status deployment/coacha-deploy -n coacha-app
kubectl rollout history deployment/coacha-deploy -n coacha-app
kubectl rollout undo deployment/coacha-deploy -n coacha-app   # rollback
kubectl rollout restart deployment/coacha-deploy -n coacha-app
kubectl scale deployment coacha-deploy --replicas=3 -n coacha-app
kubectl set image deployment/coacha-deploy coacha=coacha:5.0 -n coacha-app
```

---

### Service
IP/DNS estável que roteia para os Pods via labels.
```yaml
apiVersion: v1
kind: Service
spec:
  type: ClusterIP      # interno | NodePort = expõe no node | LoadBalancer = cloud
  selector:
    app: coacha        # roteia para pods com este label
  ports:
    - port: 80
      targetPort: 8080
```
```bash
kubectl get services -n coacha-app
kubectl describe service coacha-svc -n coacha-app
# Endpoints: mostra os IPs dos Pods que estão recebendo tráfego
```

---

### ConfigMap
Configurações não-sensíveis externalizadas do container.
```yaml
apiVersion: v1
kind: ConfigMap
data:
  APP_ENV: "production"
  APP_GREETING: "Olá do Kubernetes!"
  MAX_STUDENTS: "100"
```
```bash
kubectl apply -f k8s/configmap.yaml
kubectl edit configmap coacha-config -n coacha-app   # edita ao vivo
kubectl get configmap coacha-config -n coacha-app -o yaml
```
> Mudar o ConfigMap + `kubectl rollout restart` = nova config **sem rebuild de imagem**.

---

### Secret
Credenciais e dados sensíveis (base64 encoded).
```yaml
apiVersion: v1
kind: Secret
type: Opaque
data:
  API_KEY: bWluaGEtYXBpLWtleS1zZWNyZXRh      # base64
  DB_PASSWORD: c2VuaGEtc3VwZXItc2VjcmV0YQ==
```
```bash
# Criar sem YAML (mais seguro — não vai para o Git)
kubectl create secret generic coacha-secrets \
  --from-literal=API_KEY=valor \
  --from-literal=DB_PASSWORD=valor \
  -n coacha-app

# Decodificar
kubectl get secret coacha-secrets -n coacha-app \
  -o jsonpath='{.data.API_KEY}' | base64 -d
```
> ⚠️ Base64 não é criptografia. Em produção: **Vault**, **Sealed Secrets** ou **External Secrets Operator**.

---

### Ingress
Roteamento HTTP inteligente — um único ponto de entrada para múltiplos serviços.
```yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /$2
spec:
  ingressClassName: nginx
  rules:
    - host: coacha.local
      http:
        paths:
          - path: /api(/|$)(.*)
            backend:
              service:
                name: coacha-svc
                port:
                  number: 80
```
```bash
minikube addons enable ingress
kubectl get ingress -n coacha-app
echo "$(minikube ip) coacha.local" | sudo tee -a /etc/hosts
curl http://coacha.local/api/students
```

---

### HPA — Horizontal Pod Autoscaler
Escala automaticamente com base em CPU/memória.
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
spec:
  scaleTargetRef:
    kind: Deployment
    name: coacha-deploy
  minReplicas: 1
  maxReplicas: 5
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 50
```
```bash
minikube addons enable metrics-server
kubectl get hpa -n coacha-app -w
kubectl top pods -n coacha-app
```
> **Obrigatório:** `resources.requests.cpu` definido no Deployment para o HPA calcular %.

---

## 🛠️ kubectl — Comandos essenciais

```bash
# Inspecionar
kubectl get pods/deployments/services/all -n <ns>
kubectl get pods -n <ns> -o wide          # com IPs e node
kubectl get pods -n <ns> -w               # watch em tempo real
kubectl get all -n <ns> -o yaml           # estado real no cluster

# Debugar
kubectl describe pod <pod> -n <ns>        # Events = ouro para debug
kubectl logs <pod> -n <ns> -f             # tail em tempo real
kubectl logs <pod> -n <ns> --previous     # container que crashou
kubectl exec -it <pod> -n <ns> -- sh      # shell dentro do pod

# Acesso direto
kubectl port-forward deployment/<name> 9090:8080 -n <ns>

# Aplicar / deletar
kubectl apply -f k8s/                     # idempotente — sempre use este
kubectl delete -f k8s/
kubectl delete pod -l app=coacha -n <ns>  # por label
```

---

## 🔄 Fluxo completo praticado

```
Código Java (Spring Boot)
        ↓
docker build -t coacha:X.0 .
        ↓
minikube image load coacha:X.0
        ↓
kubectl apply -f k8s/
        ↓
┌─────────────────────────────────────┐
│  Namespace: coacha-app              │
│  ConfigMap ──┐                      │
│  Secret ─────┼─→ Deployment → Pods │
│              │         ↑           │
│              │    self-healing      │
│              │    rolling update    │
│  Service ←───┘         │           │
│     ↑                  │           │
│  Ingress               │           │
│  coacha.local/api/*    │           │
└─────────────────────────────────────┘
        ↓
curl http://coacha.local/api/students  ✅
```

---

## 📁 Estrutura final do projeto

```
coacha/
├── Dockerfile
├── .dockerignore
├── k8s/
│   ├── namespace.yaml
│   ├── configmap.yaml
│   ├── secret.yaml
│   ├── deployment.yaml
│   ├── service.yaml
│   ├── ingress.yaml
│   └── hpa.yaml
└── src/
    └── main/java/com/kaja/coacha/
        ├── controller/StudentController.java
        │     GET /students      → lista alunos
        │     GET /info          → valores do ConfigMap
        │     GET /secret-check  → confirma injeção do Secret
        ├── service/StudentService.java
        └── model/Student.java
```

---

## ⏭️ Próxima sessão — CI/CD

Temas sugeridos:

| Tema | O que estudar |
|---|---|
| **GitHub Actions** | Pipeline: build → test → docker build → push → deploy |
| **Docker Registry** | Docker Hub, GitHub Container Registry (GHCR) |
| **GitOps** | ArgoCD — cluster sincronizado com o Git automaticamente |
| **Helm** | Gerenciador de pacotes K8s — templates de manifests |
| **Kustomize** | Overlays por ambiente (dev/staging/prod) sem duplicar YAML |

### Fluxo CI/CD que vamos montar:
```
git push
    ↓
GitHub Actions (CI)
    ├── mvn test
    ├── docker build -t coacha:$SHA .
    └── docker push ghcr.io/user/coacha:$SHA
            ↓
    Atualiza deployment.yaml com nova tag
            ↓
    kubectl apply (CD) ou ArgoCD detecta e sincroniza
            ↓
    Rolling update automático no cluster ✅
```

---

> 🗒️ **Para retomar:** o cluster Minikube para quando o computador reinicia.
> Para religar: `minikube start --driver=docker`
> Para ver o estado: `kubectl get all -n coacha-app`
