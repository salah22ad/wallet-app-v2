@echo off
setlocal

:: تحديد المسار الرئيسي
set PROJECT_PATH=F:\Android_Application\My_Smart_Wallet

echo Start Deleting Folders form  %PROJECT_PATH% ...

:: Delete الFolder build في الجذر
if exist "%PROJECT_PATH%\build" (
    rmdir /s /q "%PROJECT_PATH%\build"
    echo Delete succeful Folder build
)

:: Delete Folder .idea
if exist "%PROJECT_PATH%\.idea" (
    rmdir /s /q "%PROJECT_PATH%\.idea"
    echo Delete succeful Folder .idea
)

:: Delete Folder .gradle
if exist "%PROJECT_PATH%\.gradle" (
    rmdir /s /q "%PROJECT_PATH%\.gradle"
    echo Delete succeful Folder .gradle
)

:: Delete build From app
if exist "%PROJECT_PATH%\app\build" (
    rmdir /s /q "%PROJECT_PATH%\app\build"
    echo Delete succeful Folder app\build
)
:: delete release From app
if exist "%PROJECT_PATH%\app\release" (
    rmdir /s /q "%PROJECT_PATH%\app\release"
    echo Delete succeful Folder app\release
)
echo.
echo ✅ Succefull Delete All Folders.
echo.
echo ✅ Succefull Delete All Folders.
echo.
echo 🚀 Start Cleanig Project (Gradle Clean)...

:: Move to Folder Project and Gradle Clean
cd /d %PROJECT_PATH%
gradlew clean

echo.
echo ✅ Finish Cleanig Project.
pause
endlocal