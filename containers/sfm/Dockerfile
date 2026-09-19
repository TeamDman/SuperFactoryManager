# syntax=docker/dockerfile:1
# Private experiment image: contains development Minecraft/Forge dependencies.
FROM rust:1.96.0-bookworm AS graphics-probe

RUN apt-get update && apt-get install -y --no-install-recommends \
    openjdk-17-jdk-headless xvfb xauth mesa-utils libgl1-mesa-dri libglx-mesa0 \
    libxrandr2 libxinerama1 libxcursor1 libxi6 libxxf86vm1 libasound2 \
    fonts-dejavu-core python3 ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && ln -s "$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")" /opt/java \
    && useradd --uid 10001 --create-home --shell /bin/bash sfm \
    && mkdir /workspace && chown sfm:sfm /workspace

ENV JAVA_HOME=/opt/java \
    HOME=/home/sfm \
    CARGO_HOME=/workspace/.cargo \
    SFM_PROPAGATE_CHANGES_HOME=/workspace/.sfm-config \
    SFM_PROPAGATE_CHANGES_CACHE=/workspace/.sfm-cache \
    LIBGL_ALWAYS_SOFTWARE=true \
    GALLIUM_DRIVER=llvmpipe \
    LP_NUM_THREADS=2 \
    ALSOFT_DRIVERS=null \
    JAVA_TOOL_OPTIONS="-Xmx3g -XX:ActiveProcessorCount=4" \
    CARGO_BUILD_JOBS=2

COPY containers/sfm/graphics.sh /opt/sfm-container/graphics.sh
RUN chmod 755 /opt/sfm-container/graphics.sh
USER 10001:10001
ENTRYPOINT ["/opt/sfm-container/graphics.sh"]

FROM graphics-probe AS runtime
USER root

WORKDIR /workspace
COPY --chown=sfm:sfm platform/cli/sfm-propagate-changes/ platform/cli/sfm-propagate-changes/
COPY --chown=sfm:sfm platform/cli/sfm/ platform/cli/sfm/
COPY --chown=sfm:sfm platform/minecraft/ platform/minecraft/
COPY --chown=sfm:sfm .gitignore .gitignore
COPY containers/sfm/run.sh containers/sfm/verify.py /opt/sfm-container/
RUN chmod 755 /opt/sfm-container/run.sh

ARG SFM_SOURCE_REVISION
LABEL org.opencontainers.image.revision=$SFM_SOURCE_REVISION
USER 10001:10001
# A standalone Git repo avoids references to the host's worktree administrative files.
# The actual input revision is retained separately from this synthetic local commit.
RUN test -n "$SFM_SOURCE_REVISION" \
    && printf '%s\n' "$SFM_SOURCE_REVISION" > /workspace/source-revision.txt \
    && git init -b ci-container \
    && git add . \
    && git -c user.name='SFM container fixture' -c user.email='fixture@example.invalid' commit -m 'Container source snapshot' \
    && SFM_PROPAGATE_CHANGES_INSTALL_GIT_REVISION="$SFM_SOURCE_REVISION" \
       cargo build --locked --release --manifest-path platform/cli/sfm-propagate-changes/Cargo.toml \
    && mkdir /workspace/bin \
    && cp platform/cli/sfm-propagate-changes/target/release/sfm-propagate-changes /workspace/bin/ \
    && rm -rf platform/cli/sfm-propagate-changes/target

ENV PATH=/workspace/bin:/usr/local/cargo/bin:/opt/java/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
RUN sfm-propagate-changes repo-root set /workspace \
    && sfm-propagate-changes jar build --branch ci-container --require-portable-artifacts \
    && /opt/sfm-container/run.sh prepare \
    && rm -rf /workspace/container-artifacts \
       /workspace/platform/minecraft/build/sfm-toolchain/artifacts/game-test-preview \
       /workspace/platform/minecraft/runGameTestPreview

# Runtime Cargo can only reuse the dependencies fetched during preparation.
ENV CARGO_NET_OFFLINE=true
ENTRYPOINT ["/opt/sfm-container/run.sh"]
CMD ["verify"]
