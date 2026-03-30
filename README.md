# zclaw

**Terminal-native coding agent** — a small, hackable CLI that talks to any **OpenAI-compatible** LLM API, runs tools on your repo, and keeps context under `.zclaw/` in your project and home directory.

**Repository:** [github.com/Hintic/zclaw](https://github.com/Hintic/zclaw)

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white" alt="Java 21" />
  <img src="https://img.shields.io/badge/build-Maven-C71A36?logo=apachemaven&logoColor=white" alt="Maven" />
</p>

---

## Why zclaw?

- **Single fat JAR** — no Node, no Python runtime; shade plugin bundles dependencies.
- **Tools you expect** — read/write/edit files, bash, grep, glob, task planning, optional **web search** (Gemini-style grounding via your gateway) and **browser automation** (Playwright).
- **Souls & habits** — optional persona JSON, peer “mail”, mood, and a lightweight **habit engine** for shorthand → full prompts.
- **Portable config** — `config.json` + env overrides; work dir defaults to `pwd` when using the `zclaw` launcher.

---

## Requirements

| Requirement | Version |
|-------------|---------|
| **JDK** | 21+ |
| **Maven** | 3.8+ (for building from source) |

Optional: [Playwright](https://playwright.dev/java/) browsers if you enable `browser_enabled` (`playwright install chromium` or use system Chrome via `browser_channel`).

---

## Quick start

### Build

```bash
git clone https://github.com/Hintic/zclaw.git
cd zclaw
mvn -q -DskipTests package
```

The shaded JAR is `target/zclaw-1.0-SNAPSHOT.jar`.

### Run from the repo

```bash
./run.sh
# or
java -jar target/zclaw-1.0-SNAPSHOT.jar
```

### Run from anywhere (recommended)

Put the `zclaw` script on your `PATH` (it resolves the repo dir and sets `--work-dir` to the current directory):

```bash
chmod +x zclaw
cp zclaw ~/bin/   # example
cd /path/to/your/project
zclaw                    # default soul
zclaw mysoul --model=... # named soul (see below)
```

---

## Configuration

Priority (low → high): **`~/.zclaw/config.json`** → **environment variables** → **CLI flags**.

### Config directory resolution

| Source | Effect |
|--------|--------|
| `--config-dir=/path` | Highest priority |
| `ZCLAW_CONFIG_DIR` | Explicit config root |
| `ZCLAW_HOME` | Config at `$ZCLAW_HOME/.zclaw/` |
| *(default)* | `<current working directory>/.zclaw/` |

Souls and shared data under the OS user profile use **`~/.zclaw/`** (see [Layout](#layout)).

### Environment variables (common)

| Variable | Purpose |
|----------|---------|
| `ZCLAW_API_KEY` | Bearer token for the LLM API |
| `ZCLAW_BASE_URL` | OpenAI-compatible base URL (e.g. `https://api.openai.com/v1` or your gateway) |
| `ZCLAW_MODEL` | Model name |
| `ZCLAW_API_PROVIDER` | Client flavor (e.g. `openai`) |
| `ZCLAW_MEMORY` | `true` / `false` — inject `memory.md` into system prompt |
| `ZCLAW_SOUL` | Soul id for persona / mail |
| `ZCLAW_BROWSER` | Enable Playwright browser tool |
| `ZCLAW_BROWSER_CHANNEL` | e.g. `chrome`, `msedge`, or bundled Chromium |

Web search is toggled via **`web_search_enabled`** in `config.json` or **`--web-search=true|false`** on the CLI (no dedicated `ZCLAW_*` env for it).

### Example `~/.zclaw/config.json`

```json
{
  "api_key": "sk-...",
  "base_url": "https://api.openai.com/v1",
  "model": "gpt-4o-mini",
  "api_provider": "openai",
  "web_search_enabled": true,
  "memory_enabled": true,
  "browser_enabled": false
}
```

Replace `base_url` / `model` with whatever your provider expects.

---

## Layout

```
<workDir>/.zclaw/          # project-local
├── config.json          # if you use cwd-based config dir
└── souls/
    └── <id>/
        ├── soul.json
        ├── memory.md
        └── ...

~/.zclaw/                 # user-level fallback & shared souls
└── souls/
    └── <id>.json        # legacy flat layout also supported
```

Logs default under **`~/.zclaw/logs`** (see `logback.xml`).

---

## CLI cheatsheet

| Input | Action |
|-------|--------|
| Natural language | Sent to the agent loop |
| `/help` | Built-in help |
| `/status` | Model / work dir / soul summary |
| `/clear` | Clear conversation |
| `/exit`, `/quit`, `/q` | Exit |

Use **`/help`** in the REPL for the full list and habit/soul notes.

---

## Development

```bash
mvn test              # unit tests
mvn -DskipTests package
```

Main class: `com.zxx.zclaw.ZClawMain`.

---

## Roadmap / non-goals

zclaw is intentionally **small**: no web UI, no plugin marketplace — fork and extend. PRs welcome for docs, tests, and portability.

---

## License

Add a `LICENSE` file when you publish (e.g. MIT).

---

<p align="center">
  <sub>Built with JLine · OkHttp · Gson · Playwright (optional)</sub>
</p>
