FROM mcr.microsoft.com/devcontainers/universal:linux

# Prepare the build dependencies
RUN apt-get install -y libffi-dev zlib1g-dev libbz2-dev && \
    pip install cmake && \
    mkdir -p /opt && \
    git clone https://github.com/graalvm/mx /opt/mx && \
    MX_CACHE_DIR=/opt/mxcache /opt/mx/mx -p /opt/mx -y fetch-jdk -A --to /opt --jdk-id labsjdk-ce-latest && \
    rm -rf /opt/mxcache && \
    chmod -R a+w /opt/mx && \
    echo JAVA_HOME=/opt/labsjdk-ce-latest > /opt/mx/env && \
    echo MX_GLOBAL_ENV="/opt/mx/env" >> /etc/profile && \
    echo MX_CACHE_DIR="/opt/mx/cache" >> /etc/profile && \
    echo PATH="/opt/mx:/opt/labsjdk-ce-latest/bin/:\$PATH" >> /etc/profile
