# Reproduces the alpha3 project's GitLab CI build environment.
# Source recipe: .gitlab-ci.yml (image: fedora:44, amd64).
#
# IMPORTANT: build and run this as linux/amd64. The Android NDK for Linux is
# x86_64-only, so on Apple Silicon this runs under Rosetta emulation — which is
# exactly what the project's CI runs on too.

FROM fedora:44

# --- Base toolchain (mirrors the CI's dnf installs, plus a few extras for
#     robustness while compiling the C/C++ dependency tree) ---
RUN dnf upgrade -y && \
    dnf install -y @development-tools && \
    dnf install -y \
        wget curl glibc-devel.i686 glibc-devel gcc gcc-c++ libstdc++-devel \
        unzip bzip2 cmake make which pkgconf pkg-config \
        ninja-build python3 perl patch xz findutils file diffutils \
        autoconf automake libtool tar gzip && \
    dnf clean all

# --- JDK 21 (Adoptium Temurin, exactly as the project's CI uses) ---
# Fedora 44's default OpenJDK is 25 and the standard repos don't reliably carry
# a java-21-openjdk-devel package, so we use Adoptium's repo like the CI does.
RUN printf '%s\n' \
    '[Adoptium]' \
    'name=Adoptium' \
    'baseurl=https://packages.adoptium.net/artifactory/rpm/fedora/$releasever/$basearch' \
    'enabled=1' \
    'gpgcheck=1' \
    'gpgkey=https://packages.adoptium.net/artifactory/api/gpg/key/public' \
    > /etc/yum.repos.d/adoptium.repo && \
    dnf install -y temurin-21-jdk && \
    dnf clean all
# Pin a stable JAVA_HOME via symlink (resolve the real install dir dynamically).
RUN JAVA_BIN="$(readlink -f "$(command -v java)")" && \
    JH="$(dirname "$(dirname "$JAVA_BIN")")" && \
    echo "Resolved JAVA_HOME=$JH" && \
    ln -sfn "$JH" /opt/java-current
ENV JAVA_HOME=/opt/java-current
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# --- Android SDK + NDK (versions pinned to match CI exactly) ---
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
RUN mkdir -p "$ANDROID_HOME/cmdline-tools" && \
    CLT_URL="$(curl -s https://developer.android.com/studio | grep -o 'https://dl.google.com/android/repository/commandlinetools-linux-[0-9]*_latest.zip' | head -n1)" && \
    echo "Using cmdline-tools: $CLT_URL" && \
    wget --no-verbose -O /tmp/clt.zip "$CLT_URL" && \
    unzip -q -d "$ANDROID_HOME/cmdline-tools" /tmp/clt.zip && \
    mv "$ANDROID_HOME/cmdline-tools/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest" && \
    rm /tmp/clt.zip
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"

# Accept licenses, then install the exact components the CI uses.
# (cmake;3.22.1 added explicitly so the Android Gradle Plugin's pinned CMake is
#  present in the SDK, matching what your Mac build was invoking.)
RUN yes | sdkmanager --licenses >/dev/null 2>&1 || true && \
    sdkmanager \
        "platform-tools" \
        "platforms;android-36" \
        "build-tools;35.0.0" \
        "ndk;29.0.14206865" \
        "cmake;3.22.1"

WORKDIR /workspace
CMD ["/bin/bash"]
