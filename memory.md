<think>
The user asked a simple greeting question ("你好，你叫什么名字") and the assistant gave a friendly response introducing themselves as "zclaw". 

I need to merge this into the existing memory.md. Since the user message is just a greeting and doesn't contain any project-specific information, conventions, or decisions, there's nothing meaningful to add to the memory file. The exchange is just a basic introduction.

I should keep the existing memory content unchanged and not add anything from this exchange since it's not relevant to project workflow or conventions.
</think>

# Project Memory

## Workflow
- User prefers **spec-driven development**: spec first → review → task breakdown → execute → curl verify
- Future specs go in `docs/spec-*.md`
- Curl verification uses `http://127.0.0.1:8080` (local), not FAT environment
- Don't ask about starting the service — just do `mvn spring-boot:run`
- Before starting, check `lsof -t -i :8080` and kill residual processes

## Git / Deploy Flow
- **Remote**: `git@git.dev.sh.ctripcorp.com:empowerment-ai-labs/peta-manager-service.git`
- **Deploy trigger**: push to `release-fat` branch → CI builds Docker image automatically
- **每次新功能的 git 流程**:
  1. `git fetch origin release-fat`
  2. `git checkout -b dev_zxx_${YYYYMMDD} origin/release-fat` — 从最新 release-fat 创建开发分支
  3. 开发 + commit
  4. `git push origin dev_zxx_${YYYYMMDD}` — 推送开发分支到远端
  5. `git checkout release-fat && git pull origin release-fat` — 确保本地 release-fat 最新
  6. `git merge dev_zxx_${YYYYMMDD}` — 本地合并（不需要 force push）
  7. `git push origin release-fat` — 触发 CI 构建
- **分支命名**: `dev_zxx_${YYYYMMDD}`（如 `dev_zxx_0324`）
- 不需要 force push，正常 merge 即可

## Pitfalls
- **Tomcat dependency**: `spring-boot-starter-tomcat` must use `<scope>provided</scope>`, NOT `<exclusions>` on `tomcat-embed-core`. Exclusions break `mvn spring-boot:run` (no embedded container). See [debugging.md](debugging.md) for details.
- **Baiji SpecificRecord**: Boolean fields use `isEnabled()` not `getEnabled()`. Same for all Boolean getters in contract JAR classes.
- **Spring Boot startup is slow** (~7+ min) due to heavy enterprise framework (QConfig, CAT, Artemis, etc.)

## Conventions
- All external controller endpoints use `@PostMapping` + `@RequestBody` (no GET for API endpoints)
- Contract JAR: `com.ctrip.basebiz.ai:peta.manager.service:1.5.0-SNAPSHOT`, force refresh with `mvn dependency:resolve -U`
