@echo off
echo.
echo ========================================
echo   Olo Chat Backend
echo ========================================
echo.
echo   Backend:  http://localhost:7080
echo   Swagger:  http://localhost:7080/swagger-ui.html
echo.
echo ========================================
echo.
call gradlew.bat bootRun
pause
