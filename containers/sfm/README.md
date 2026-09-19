# Minecraft client container experiment

Run the graphics probe first, then the complete SFM puppet fixture. These are Linux
containers. Docker Desktop must use its Linux backend on Windows; no host display,
GPU device, Minecraft account, or Discord token is passed into either fixture.

## Run the independent graphics probe

From the repository root, in Bash with a running Docker daemon:

```bash
docker build --target graphics-probe -f containers/sfm/Dockerfile -t sfm-graphics:local .
docker run --rm --network none --read-only --cap-drop ALL \
  --security-opt no-new-privileges:true --pids-limit 128 --memory 1g --cpus 2 \
  --tmpfs /tmp:rw,exec,nosuid,nodev,size=128m,mode=1777 \
  --tmpfs /home/sfm:rw,nosuid,nodev,size=16m,uid=10001,gid=10001,mode=700 \
  sfm-graphics:local
```

Success prints `GRAPHICS_PROBE_PASSED renderer=llvmpipe`, the OpenGL versions,
and the asserted isolation settings. This proves the software graphics stack;
it does not prove Minecraft or SFM launches.

Xvfb supplies an in-memory X display. Mesa's llvmpipe renders OpenGL on the CPU.
The fixture sets `LIBGL_ALWAYS_SOFTWARE=true` and `GALLIUM_DRIVER=llvmpipe`, then
checks the actual renderer reported by `glxinfo`.
[Xvfb manual](https://xorg.freedesktop.org/archive/X11R7.0/doc/html/Xvfb.1.html),
[Mesa llvmpipe](https://docs.mesa3d.org/drivers/llvmpipe.html),
[Mesa environment variables](https://docs.mesa3d.org/envvars.html).

## Run SFM and capture the world

```bash
docker build --build-arg SFM_SOURCE_REVISION="$(git rev-parse HEAD)" \
  -f containers/sfm/Dockerfile -t sfm-ci:local .
bash containers/sfm/smoke.sh sfm-ci:local build/container-smoke
```

The image prepares public dependencies and builds with the canonical Rust
`sfm-propagate-changes` tool. It runs `title_screen_capture` and
`game_test_orbit_capture` for `sfm:move_1_stack_direct` during preparation, then
removes those screenshots. Each puppet launches a fresh client under the same
Xvfb display: the title fixture needs the startup loading overlay before any
world is entered. The smoke script repeats both launches with networking
disabled and requires an independent successful completion marker, three title
captures, and eight world captures. Each manifest-referenced PNG must exist with the
reported dimensions. Gradle is not used.

The image has a synthetic `ci-container` Git branch because a host worktree's
`.git` pointer cannot be used inside a container. The supplied source revision is
recorded in the image label and `source-revision.txt`. The snapshot contains the
build context, including uncommitted changes if invoked locally; the revision
alone does not attest a clean local checkout. CI supplies its checked-out revision.

The first build downloads and compiles the complete toolchain and can take tens
of minutes. A cached repeat should be much shorter; the smoke wrapper imposes a
35-minute wall-clock limit for the two client launches. Software rendering performance remains a measured
property of the runner. The image currently keeps Rust and Cargo caches because
the canonical puppet launcher builds the checkout-local `sfm` control CLI on
every invocation.

Evidence is copied to `build/container-smoke` even when the game fails:

- `glxinfo.txt` and `isolation.txt`: actual renderer and runtime assertions.
- `title_screen_capture/` and `game_test_orbit_capture/`: separate `console.log`,
  `game-logs/`, `exit-code.txt`, and `previews/` with the existing SFM HTML preview,
  manifest, and screenshots.
- `docker.log`: container launch and failure diagnostics.
- `verification.json`, `exit-code.txt`, and `source-revision.txt`: result and input.
- `docker-inspect.json`: the container configuration and final process status.

Game-instance descriptors and the home directory are excluded because they can
contain authentication tokens. A failed image build has no runtime container to
inspect; its build log is the evidence in that case. A downloaded dependency that
cannot reproduce the locked hash must fail rather than use a host-only cache.

## Isolation boundary

The smoke script runs as UID 10001 with no Linux capabilities, no privilege
escalation, Docker's seccomp filter, no external network interfaces, a read-only
root filesystem, and explicit CPU, memory, PID, and wall-time limits. It exposes
no ports and mounts neither host directories nor the Docker socket. A fresh
anonymous volume receives the image's prepared workspace; it is removed with the
container. Only bounded temporary directories and that workspace are writable.
`/tmp` permits execution because LWJGL extracts native libraries there.

This is useful isolation for the repository's controlled game fixture. The
anonymous workspace volume does not have a disk quota. A production worker needs
bounded persistent storage and stronger separation before accepting arbitrary
mods or executable uploads. Containers rely on the host kernel;
[Docker's security model](https://docs.docker.com/engine/security/) explains the
remaining boundary. Kubernetes recommends a VM or userspace-kernel sandbox for
untrusted code in shared clusters.
[Kubernetes workload sandboxing](https://kubernetes.io/docs/concepts/security/multi-tenancy/#sandboxing-containers).

## Fit for a Discord help worker

1. Keep the Discord bot and its token in a separate controller. Accept a bounded
   help request, choose a trusted mod image and fixture, and enqueue a session.
2. Start one disposable game worker per request. Pass only the requested SFM
   program or a validated world input; do not turn chat text into shell commands,
   arbitrary CLI arguments, container options, or image names.
3. Run the existing control CLI inside the worker. The game's
   `SFMClientControlServer` binds a random `127.0.0.1` port and uses a per-instance
   token. The CLI must share the game network namespace and descriptor directory.
   Publishing a container port alone does not make that loopback service usable.
4. Return only selected logs and screenshots to the controller, with size and
   retention limits. Destroy the game process, world, writable caches, and control
   descriptors after completion or timeout.

The repository already has authenticated Vox control in `platform/cli/sfm` and
the reusable orbit fixture under
`platform/minecraft/src/gametest/java/ca/teamdman/sfm/gametest/puppet/definition/`.
Those are the integration points. This experiment does not create a Discord
application, install a bot, or expose its game-control service externally.

## Kubernetes mapping

The same Xvfb/Mesa stack runs within a pod and needs no GPU resource request.
Prepare an image before job submission; a worker should start offline with all
required artifacts present.

| Docker experiment | Kubernetes worker equivalent |
| --- | --- |
| Nonroot, dropped capabilities, no privilege escalation | `runAsUser: 10001`, `runAsNonRoot: true`, drop `ALL`, `allowPrivilegeEscalation: false`, `seccompProfile.type: RuntimeDefault` |
| Read-only root plus disposable workspace | `readOnlyRootFilesystem: true`; `emptyDir` with size limit plus ephemeral-storage requests/limits; populate it from the image in an init container |
| CPU, memory, PID, and wall-time limits | Container resource requests/limits, node pod-PID limit, Job `activeDeadlineSeconds`, bounded queue/concurrency |
| No external networking | Enforced default-deny ingress and egress NetworkPolicies; controller transfers input/output through a narrow broker |
| No host authority or durable credentials | No `hostPath`, host network, privileged container, or Docker socket; `automountServiceAccountToken: false` |

Enforce the
[Restricted Pod Security Standard](https://kubernetes.io/docs/concepts/security/pod-security-standards/#restricted)
and use a sandboxed `RuntimeClass` for untrusted workloads. A namespace alone does
not provide this separation. NetworkPolicies require a network plugin that
enforces them, and storage limits need node-level monitoring and eviction behavior
to be validated. The experiment's Docker volume copy-up is Docker-specific;
Kubernetes `emptyDir` starts empty, so an init container must seed it explicitly.
