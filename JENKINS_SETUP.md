# Jenkins + SonarQube + Linters — local CI lab

What the pipeline does (`Jenkinsfile`):

| Stage | Command | Fails build when |
|---|---|---|
| Checkout | `checkout scm` | repo/branch/creds wrong |
| Build | `mvn clean compile` | compile error |
| Lint | `mvn checkstyle:check spotbugs:spotbugs` | Checkstyle `error` rule hit (empty catch, unused import, equals w/o hashCode...) |
| Test | `mvn test` (+ JaCoCo coverage) | any JUnit test fails |
| SonarQube Analysis | `mvn sonar:sonar` | scanner can't reach/auth Sonar |
| Quality Gate | `waitForQualityGate` | Sonar gate fails (new bugs, coverage on new code, etc.) |
| Package | `mvn package -DskipTests` + archive jar | packaging error |

Files added: `Jenkinsfile`, `docker-compose.yml`, `jenkins/Dockerfile`, `jenkins/plugins.txt`,
`config/checkstyle/checkstyle.xml`, `src/test/java/com/demo/DemoApplicationTests.java`, `.gitignore`, pom plugins.

---

## Step 0 — Verify locally first (needs JDK 17 + Maven)
```bash
mvn -B clean verify checkstyle:check spotbugs:spotbugs
```
If this fails on your laptop it will fail in Jenkins. CI only automates what already works.

## Step 1 — Push to GitHub
```bash
git init && git add . && git commit -m "Add Jenkins pipeline, Checkstyle, SpotBugs, JaCoCo, Sonar"
git branch -M main
git remote add origin https://github.com/<you>/spring-annotations-demo.git
git push -u origin main
```

## Step 2 — Start Jenkins + SonarQube
Docker Desktop: give it >= 6 GB RAM (SonarQube runs Elasticsearch).
```bash
docker compose up -d --build
docker exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword
```
- Jenkins: http://localhost:8080 → paste password → **Select plugins → None** (already baked in) → create admin.
- SonarQube: http://localhost:9000 → admin / admin → change password.

## Step 3 — First pipeline run (no Sonar yet)
1. Jenkins → **New Item** → name `spring-annotations-demo` → **Pipeline**.
2. Pipeline → Definition: **Pipeline script from SCM** → Git → repo URL, branch `*/main`, Script Path `Jenkinsfile`.
   Private repo: add a GitHub PAT as *Username with password* credential.
3. **Build with Parameters** → untick `RUN_SONAR` → Build.
4. Look at: Stage View, Console Output, **Tests**, **Coverage**, **CheckStyle/SpotBugs warnings** on the build page.

## Step 4 — Wire SonarQube
1. Sonar → My Account → Security → **Generate token** (type: Global analysis).
2. Jenkins → Manage Jenkins → Credentials → add **Secret text**, ID `sonar-token`, value = token.
3. Jenkins → Manage Jenkins → System → **SonarQube servers** → tick env vars injection →
   Name `sonarqube` (must match Jenkinsfile), URL `http://sonarqube:9000`, token `sonar-token`.
4. Sonar → Administration → Configuration → **Webhooks** → Create:
   URL `http://jenkins:8080/sonarqube-webhook/` (without this, Quality Gate stage hangs until timeout).
5. Build with `RUN_SONAR` ticked. Open http://localhost:9000 → project `spring-annotations-demo`.

## Step 5 — Break it on purpose (this is how you actually learn it)
| Break | Expected red stage |
|---|---|
| Add `import java.util.Date;` unused | Lint |
| `try { ... } catch (Exception e) {}` | Lint |
| Change test to expect 201 | Test |
| Delete a semicolon | Build |
| Stop sonarqube container | SonarQube Analysis |

## Step 6 — Auto-trigger
Localhost Jenkins can't receive GitHub webhooks, so: job → Configure → Triggers → **Poll SCM** `H/5 * * * *`.
(With a public Jenkins URL: GitHub repo → Settings → Webhooks → `https://<jenkins>/github-webhook/`.)

---

## GitHub Actions ↔ Jenkins (for interviews)
| GitHub Actions | Jenkins |
|---|---|
| `.github/workflows/ci.yml` | `Jenkinsfile` (Groovy DSL) |
| `runs-on: ubuntu-latest` | `agent any` / `agent { label 'linux' }` / `agent { docker {...} }` |
| `jobs.<id>.steps` | `stages { stage { steps } }` |
| `actions/checkout` | `checkout scm` |
| Secrets (`${{ secrets.X }}`) | Credentials + `withCredentials` / `withSonarQubeEnv` |
| `on: push / pull_request` | Webhook / Poll SCM / Multibranch Pipeline |
| Marketplace actions | Plugins |
| `actions/upload-artifact` | `archiveArtifacts` |
| `if:` | `when { }` |
| matrix | `matrix { }` / `parallel { }` |
| Hosted runners | You run/maintain controller + agents |

**Honest framing:** "Current team is on GitHub Actions. Previously / in parallel I've set up Jenkins declarative
pipelines: Maven build, Checkstyle + SpotBugs gates, JUnit + JaCoCo, SonarQube quality gate via webhook,
artifacts archived. Same concepts, Jenkins is self-hosted and plugin-driven."

## Next steps (in order)
1. Multibranch Pipeline (build every branch/PR automatically).
2. Run builds in a Docker agent: `agent { docker { image 'maven:3.9-eclipse-temurin-17' } }`.
3. Build + push a Docker image stage; deploy stage with manual `input` approval.
4. Shared Library (reuse one pipeline across services).
5. Jenkins Configuration as Code (JCasC) so Step 4 is code, not clicks.
