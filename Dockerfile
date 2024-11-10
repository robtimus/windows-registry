# escape=`

# nanoserver doesn't support KtmW32
FROM mcr.microsoft.com/windows/servercore:ltsc2022
RUN mkdir C:\tools `
    && mkdir C:\tools\java `
    && curl -L --output C:\tools\java\jdk.zip https://github.com/adoptium/temurin23-binaries/releases/download/jdk-23.0.1%2B11/OpenJDK23U-jdk_x64_windows_hotspot_23.0.1_11.zip `
    && tar xzf C:\tools\java\jdk.zip -C C:\tools\java `
    && del C:\tools\java\jdk.zip `
    && mkdir C:\tools\maven `
    && curl -L --output C:\tools\maven\maven.zip https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip `
    && tar xzf C:\tools\maven\maven.zip -C C:\tools\maven `
    && del C:\tools\maven\maven.zip
ENV JAVA_HOME=C:\tools\java\jdk-23.0.1+11
ENV MAVEN_HOME=C:\tools\maven\apache-maven-3.9.9
USER ContainerUser
# Use setx instead of ENV to append to the current value; ${PATH} etc don't seem to work
RUN setx PATH "%PATH%;%JAVA_HOME%\bin;%MAVEN_HOME%\bin"

WORKDIR C:/workspace
CMD mvn -Dmaven.repo.local=C:/repository verify -Pintegration-tests
