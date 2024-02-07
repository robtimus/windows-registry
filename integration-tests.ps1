$mavenRepository = mvn help:evaluate -Dexpression="settings.localRepository" -q -DforceStdout

Write-Output 'Building Docker image...'
docker build -t com.github.robtimus/windows-registry .
Write-Output 'Successfully built Docker image'
#if %errorlevel% neq 0 exit /b %errorlevel%

Write-Output 'Running Docker image...'
$currentDir = $pwd | Select -ExpandProperty Path
docker run --rm -v "${currentDir}:C:/workspace" -v "${mavenRepository}:C:/repository" com.github.robtimus/windows-registry
Write-Output 'Successfully ran Docker image'
#if %errorlevel% neq 0 exit /b %errorlevel%
