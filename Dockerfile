# escape=`

FROM mcr.microsoft.com/windows/nanoserver:20H2
RUN mkdir C:\tools `
    && mkdir C:\tools\java `
    && curl -L --output C:\tools\java\jdk.zip https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.14%2B9/OpenJDK11U-jdk_x64_windows_hotspot_11.0.14_9.zip `
    && tar xzf C:\tools\java\jdk.zip -C C:\tools\java `
    && del C:\tools\java\jdk.zip `
    && mkdir C:\tools\maven `
    && curl -L --output C:\tools\maven\maven.zip https://dlcdn.apache.org/maven/maven-3/3.8.4/binaries/apache-maven-3.8.4-bin.zip `
    && tar xzf C:\tools\maven\maven.zip -C C:\tools\maven `
    && del C:\tools\maven\maven.zip
ENV JAVA_HOME=C:\tools\java\jdk-11.0.14+9
ENV MAVEN_HOME=C:\tools\maven\apache-maven-3.8.4
# Use setx instead of ENV to append to the current value; ${PATH} etc don't seem to work
RUN setx PATH "%PATH%;%JAVA_HOME%\bin;%MAVEN_HOME%\bin"

WORKDIR C:/workspace
CMD mvn -Dmaven.repo.local=.repository verify -Pintegration-tests
