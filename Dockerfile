ARG NETSHOT_VERSION=0.0.1-dev
ARG GRAALVM_VERSION=21.0.9
ARG NODE_VERSION=22
ARG DEBIAN_VERSION=13

FROM node:${NODE_VERSION}-alpine AS webui-builder
WORKDIR /build/webui
COPY src/main/resources/webui/package.json src/main/resources/webui/package-lock.json ./
RUN npm ci
COPY src/main/resources/webui ./
RUN npm run build

FROM debian:${DEBIAN_VERSION} AS debian-graalvm
ARG GRAALVM_VERSION
RUN apt-get -y update && apt-get -y install wget fontconfig
WORKDIR /usr/lib/jvm
RUN wget --quiet https://download.oracle.com/graalvm/${GRAALVM_VERSION%%.*}/archive/graalvm-jdk-${GRAALVM_VERSION}_linux-x64_bin.tar.gz && \ 
    tar xvzf graalvm-jdk-${GRAALVM_VERSION}_linux-x64_bin.tar.gz && \
    rm -f graalvm-jdk-${GRAALVM_VERSION}_linux-x64_bin.tar.gz && \
    JDIR=$(ls -d graalvm-jdk-${GRAALVM_VERSION}* | tail -n 1) && \
    mkdir ${JDIR}/languages && \
    update-alternatives --install /usr/bin/java java /usr/lib/jvm/${JDIR}/bin/java 92100

FROM debian-graalvm AS debian-graalvm-graalpy
ARG NETSHOT_VERSION
COPY pom.xml mvnw /build/
COPY .mvn /build/.mvn
WORKDIR /build
RUN GRAALPY_VERSION=$(JAVA_HOME=/ ./mvnw help:evaluate -Dexpression=polyglot.version -q -DforceStdout) && \
    echo GRAALPY_VERSION && \
    wget --quiet https://github.com/oracle/graalpython/releases/download/graal-${GRAALPY_VERSION}/graalpy-jvm-${GRAALPY_VERSION}-linux-amd64.tar.gz && \
    tar xvzf graalpy-jvm-${GRAALPY_VERSION}-linux-amd64.tar.gz && \
    mv graalpy-${GRAALPY_VERSION}-linux-amd64 graalpy

FROM debian-graalvm-graalpy AS builder
ARG NETSHOT_VERSION
WORKDIR /build
RUN ./mvnw dependency:resolve dependency:resolve-plugins -B
COPY . /build
COPY --from=webui-builder /build/webui/dist /build/src/main/resources/webui/dist
RUN sed -i -r "s/VERSION = \".*\";/VERSION = \"$NETSHOT_VERSION\";/g" \
       src/main/java/net/netshot/netshot/Netshot.java
RUN ./mvnw package -Dmaven.test.skip

FROM debian-graalvm
RUN mkdir /usr/local/netshot /var/log/netshot
COPY --from=builder /build/graalpy /usr/lib/graalpy
COPY --from=builder /build/target/netshot.jar /usr/local/netshot/netshot.jar
COPY dist/netshot.conf.docker /etc/netshot.conf
EXPOSE 8080
CMD ["/usr/bin/java", "-jar", "/usr/local/netshot/netshot.jar"]
