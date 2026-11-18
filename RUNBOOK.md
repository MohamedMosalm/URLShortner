# Runbook: run the CI/CD flow and test the app in the Codespace

How a `git push` becomes a running, tested app:

```
git push -> Jenkins (build + push image) -> commit new tag to k8s/app.yaml -> Argo CD syncs -> pods update
```

Everything below runs in the Codespace terminal. Ports used: Jenkins 8081, Argo CD 8082, app 8080.

## 0. Prerequisites

- Codespace is running (a 4-core machine is recommended).
- Jenkins has the credentials `dockerhub` and `github` (Manage Jenkins -> Credentials).
- The Docker Hub repo `mohamedmosalm/url-shortener` is public.
- Argo CD is installed, `server.insecure` is set to `true`, and `argocd/application.yaml` is applied.
- The JWT secret exists in the cluster. It is not stored in git. Create it once (the namespace is created by Argo CD on the first sync):
  ```bash
  kubectl create namespace url-shortener --dry-run=client -o yaml | kubectl apply -f -
  kubectl create secret generic app-auth -n url-shortener --from-literal=secret="$(openssl rand -hex 32)"
  ```
  Pods stay in `CreateContainerConfigError` until this secret exists. Changing it later logs everybody out.

## 1. Start everything

After a Codespace restart, minikube and Jenkins are stopped. Start them again.

```bash
minikube status || minikube start --driver=docker
kubectl get nodes

cd /workspaces/URLShortner
pgrep -f jenkins.war || nohup java -jar jenkins.war --httpPort=8081 > jenkins.log 2>&1 &
```

Check the cluster:

```bash
kubectl get pods -n argocd
kubectl get pods -n url-shortener
```

Every pod must be `Running`. Wait a minute after a restart.

## 2. Open the dashboards

Argo CD needs its own terminal that stays open:

```bash
kubectl port-forward svc/argocd-server -n argocd 8082:80 --address 0.0.0.0
```

Open the Ports tab and click the globe icon on 8082 (Argo CD) and 8081 (Jenkins).

Argo CD login: `admin` and the password from

```bash
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath="{.data.password}" | base64 -d; echo
```

Healthy state: the `url-shortener` tile shows `Synced` and `Healthy`.

## 3. Test the real app

Port-forward the app (own terminal, leave it open):

```bash
kubectl port-forward svc/url-shortener 8080:8080 -n url-shortener --address 0.0.0.0
```

### Option 1: Swagger UI in the browser

Open port 8080 from the Ports tab (globe icon) and add `/swagger-ui.html` to the address.

1. Run `POST /auth/register`, then `POST /auth/login`, and copy the `token` from the answer.
2. Click **Authorize** at the top, paste the token (without the word Bearer) and confirm.
3. Try `POST /api/links`, `GET /api/links`, and the other calls with **Try it out**.
4. Test the redirect by opening `<port 8080 address>/<code>` in a new browser tab. Swagger cannot follow a 302 properly.

The raw API description is at `/v3/api-docs`. The lock icon also shows on the public calls, but they work without a token.

### Option 2: curl

In another terminal:

```bash
# register and log in
curl -i -X POST localhost:8080/auth/register -H "Content-Type: application/json" -d '{"username":"alice","password":"min-8-chars"}'
TOKEN=$(curl -s -X POST localhost:8080/auth/login -H "Content-Type: application/json" -d '{"username":"alice","password":"min-8-chars"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
AUTH="Authorization: Bearer $TOKEN"

# create a short link and keep its code
curl -s -X POST localhost:8080/api/links -H "$AUTH" -H "Content-Type: application/json" -d '{"url":"https://spring.io"}' | tee /dev/stderr | sed 's/.*"shortCode":"\([^"]*\)".*/\1/' > /tmp/code
CODE=$(cat /tmp/code); echo "code: $CODE"

# list, read, redirect
curl -H "$AUTH" localhost:8080/api/links
curl -H "$AUTH" localhost:8080/api/links/$CODE
curl -i localhost:8080/$CODE

# delete
curl -i -X DELETE -H "$AUTH" localhost:8080/api/links/$CODE
```

Expected results:

| Call | Result |
|---|---|
| register | 201 (409 if the user exists) |
| create link | 201 with `shortCode`, `shortUrl`, `longUrl` |
| list or read without the token | 401 |
| redirect | 302 with a `Location` header to the original URL |
| read or delete of an unknown code | 404 |
| URL without http or https | 400 |

### Look inside Postgres and Redis

```bash
kubectl exec deploy/postgres -n url-shortener -- psql -U postgres -d urlshortener -c "select * from links;"
kubectl exec deploy/redis -n url-shortener -- redis-cli keys '*'
```

After you call the redirect once, Redis holds a key like `links::<code>`. After the delete call the key is gone, because the app clears the cache entry.

## 4. Run the CI/CD flow

Waiting is the slow part of a live demo: Jenkins polls every 2 minutes, the build takes about 1 to 3 minutes, and Argo CD refreshes every 3 minutes. To skip the waiting, click **Build Now** in Jenkins right after the push, and click **Refresh** on the `url-shortener` tile in Argo CD once Jenkins' commit lands.

Always `git pull` before you edit, because Jenkins pushes commits to the same branch and a push from a stale copy is rejected.

### A. Code change goes through Jenkins and Argo CD

1. Make a visible change (for example edit a log message in `LinkService.java`), then:
   ```bash
   git pull
   git add -A && git commit -m "Change log message" && git push
   ```
2. Jenkins polls every 2 minutes. Open the job and watch the new build:
   - stage 1 builds and pushes `mohamedmosalm/url-shortener:<build>`
   - stage 2 commits `image: ...:<build>` into `k8s/app.yaml`
3. Pull Jenkins' commit and check it:
   ```bash
   git pull
   git log --oneline -3
   grep image: k8s/app.yaml
   ```
4. Argo CD refreshes every 3 minutes. Watch the rollout:
   ```bash
   kubectl get pods -n url-shortener -w
   kubectl get deploy url-shortener -n url-shortener -o jsonpath='{.spec.template.spec.containers[0].image}'; echo
   ```
   The image must end in the new build number. Run the tests from step 3 again.

Jenkins' own commit contains `[skip ci]`, so no new build starts from it. If a build starts anyway, the loop guard is broken.

### B. Change replicas through git only

Run `git pull`, change `replicas: 2` to `4` in `k8s/app.yaml`, then commit and push. Jenkins builds too, since any push counts. Argo CD syncs and you get 4 pods.

### C. Self-heal

```bash
kubectl delete deploy url-shortener -n url-shortener
kubectl get pods -n url-shortener -w
```

Argo CD recreates the deployment from git.

### D. Roll back

Revert the last Jenkins commit and push:

```bash
git revert --no-edit HEAD && git push
```

Argo CD returns to the previous image tag.

## 5. Stop for the day

```bash
pkill -f jenkins.war
minikube stop
```

Then stop the Codespace from github.com/codespaces to save hours.

## Troubleshooting

| Symptom | Check |
|---|---|
| Pod `ImagePullBackOff` | Docker Hub repo is private, or the tag does not exist |
| Pod `CrashLoopBackOff` | `kubectl logs deploy/url-shortener -n url-shortener` |
| `InvalidImageName` | `image:` in `k8s/app.yaml` has a typo or uppercase letters |
| Argo CD `Unknown` | `kubectl describe application url-shortener -n argocd`; the repo needs credentials if private |
| Jenkins stage 1 `insufficient scopes` | Docker token must be Read and Write |
| Jenkins stage 2 `Invalid username or token` | `github` credential must be a token with `repo` scope |
| Argo CD page will not open | `server.insecure` patched, and port-forward uses `8082:80` |
| Pod `CreateContainerConfigError` | The secret `app-auth` is missing in namespace `url-shortener` |
| `relation "users" does not exist` after wiping Postgres | `schema.sql` only runs at app start: `kubectl rollout restart deploy/url-shortener -n url-shortener` |
| Data gone | Postgres data lives in the `postgres-data` volume. It survives pod restarts and `minikube stop`, but not `kubectl delete pvc postgres-data` or `minikube delete` |
