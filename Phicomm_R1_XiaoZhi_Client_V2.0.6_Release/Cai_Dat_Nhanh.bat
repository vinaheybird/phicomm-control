@echo off
chcp 65001>nul 2>nul
setlocal ENABLEDELAYEDEXPANSION
title Cong Cu Cai Dat XiaoZhi Cho Phicomm R1 V2.0.6
cd /d "%~dp0"

rem  Cach dung:
rem    Nhay dup chuot vao file         -> Menu tuong tac
rem    一键安装.bat [IP_Loa] [So_Lenh]  -> Chay truc tiep khong can menu [so lenh xem trong menu]
rem    Vi du: 一键安装.bat 192.168.2.5:5555 1                 Cai dat toan bo
rem           一键安装.bat 192.168.2.5:5555 5 192.168.2.189   Dien dia chi server noi bo

set "ADB=%~dp0adb.exe"
if not exist "%ADB%" (
  where adb.exe >nul 2>nul
  if errorlevel 1 (
    color 04
    echo.
    echo  [LOI] Khong tim thay adb.exe!
    echo  Vui long dat adb.exe / AdbWinApi.dll / AdbWinUsbApi.dll vao cung thu muc voi file bat nay.
    echo  [Hoac them duong dan platform-tools vao bien moi truong PATH cua he thong.]
    echo.
    pause
    exit /b 1
  )
  set "ADB=adb.exe"
)
set "DEFIP=192.168.88.66:5555"
set "ARGIP=%~1"
set "ARGDO=%~2"
set "ARGSRV=%~3"
set "JP2="
set "PLYR="
set "PURE="
set "DEVIP="
set "ip="
set "type="

echo.
echo  ==========================================================
echo     PHICOMM R1 CHUYEN DUNG XIAOZHI - CONG CU CAI DAT V2.0.6
echo  ==========================================================
echo   Cai dat [XiaoZhi Thay The] va [So Host] vao loa R1,
echo   giu nguyen tinh nang Beamforming 4 mic / Khuu on / AGC / AEC goc cua loa.
echo   Ban V2.0.6: Lam lai he thong ghi nho va bao cao tien do phat,
echo               tua nhanh / lui / doc thoi luong bang giong noi.
echo.

for %%f in ("XiaoZhi_Unisound_V*.apk") do set "JP2=%%~nxf"
if not defined JP2 for %%f in ("apps\XiaoZhi_Unisound_V*.apk") do set "JP2=apps\%%~nxf"
for %%f in ("XiaoZhi_EchoService_V*.apk") do set "PLYR=%%~nxf"
if not defined PLYR for %%f in ("apps\XiaoZhi_EchoService_V*.apk") do set "PLYR=apps\%%~nxf"
for %%f in ("XiaoZhi_SoHost_V*.apk") do set "PURE=%%~nxf"
if not defined PURE for %%f in ("apps\XiaoZhi_SoHost_V*.apk") do set "PURE=apps\%%~nxf"

rem  Doc phien ban truc tiep tu ten file APK
set "JPV="
set "PLV="
if defined JP2 for %%f in ("%JP2%") do set "JPV=%%~nf"
if defined JPV set "JPV=%JPV:XiaoZhi_Unisound_V=%"
if defined PLYR for %%f in ("%PLYR%") do set "PLV=%%~nf"
if defined PLV set "PLV=%PLV:XiaoZhi_EchoService_V=%"

echo  [OK] Phat hien goi chinh [XiaoZhi Device] : V%JPV%    %JP2%
echo  [OK] Phat hien goi dieu khien [Console]  : pl-%PLV%   %PLYR%
echo  [OK] Phat hien goi so host [Opus Hal]     : %PURE%
echo.
if not defined JP2 echo  [CANH BAO] Khong tim thay XiaoZhi_Unisound_V*.apk, vui long dat vao thu muc nay hoac appsif not defined PLYR echo  [CANH BAO] Khong tim thay XiaoZhi_EchoService_V*.apk, vui long dat vao thu muc nay hoac appsif not defined PURE echo  [CANH BAO] Khong tim thay XiaoZhi_SoHost_V*.apk, vui long dat vao thu muc nay hoac apps
:connect
echo.
echo  ------------ BUOC 1: KET NOI TOI LOA ------------
if defined ARGIP (
  set "ip=%ARGIP%"
) else (
  echo  Nhap IP va cong cua loa [Vi du: 192.168.2.5:5555]
  set /p "ip=  Nhan Enter dung mac dinh %DEFIP%, dung cap USB thi nhap usb : "
  if not defined ip set "ip=%DEFIP%"
)
if /i "%ip%"=="usb" (
  set "type=-d"
) else (
  set "type=-s %ip%"
)
echo  Dang ket noi toi %ip% ...
if /i not "%ip%"=="usb" "%ADB%" connect %ip% >nul <nul
"%ADB%" %type% shell ls >nul 2>nul <nul
if errorlevel 1 (
  color 04
  echo.
  echo  [LOI] KET NOI THAT BAI! Vui long kiem tra:
  echo    1. Loa da duoc bat nguon va khoi dong xong chua?
  echo    2. Tinh nang Go loi mang ADB [port 5555] tren loa da bat chua?
  echo    3. Dia chi IP va cong da nhap chinh xac chua?
  echo    4. May tinh va loa co dang ket noi chung mot mang WiFi/LAN khong?
  echo.
  if defined ARGIP exit /b 2
  pause
  color 0a
  goto connect
)
color 0a
echo  [OK] Ket noi thanh cong!
echo.

if /i "%ip%"=="usb" (
  "%ADB%" %type% shell getprop dhcp.wlan0.ipaddress > "%TEMP%1_ip.txt" <nul
  set /p DEVIP=< "%TEMP%1_ip.txt"
) else (
  for /f "tokens=1 delims=:" %%a in ("%ip%") do set "DEVIP=%%a"
)
"%ADB%" %type% shell getprop ro.build.version.incremental > "%TEMP%1_v.txt" <nul
set /p fwv=< "%TEMP%1_v.txt"
"%ADB%" %type% shell getprop ro.product.model > "%TEMP%1_m.txt" <nul
set /p fwm=< "%TEMP%1_m.txt"
"%ADB%" %type% shell getprop ro.serialno > "%TEMP%1_s.txt" <nul
set /p fws=< "%TEMP%1_s.txt"
echo  Model: %fwm%    Firmware: %fwv%    So SN: %fws%
echo  IP cua loa: %DEVIP%    [Web Console: http://%DEVIP%:8080/ ]
echo.

set "FWOK=0"
set "FW4=%fwv:~0,4%"
if "%FW4%"=="3448" set "FWOK=1"
if "%FWOK%"=="0" (
  color 06
  echo  ----------------------------------------------------------
  echo   [FIRMWARE KHONG DUNG - KHOA CHUC NANG CAI DAT]
  echo     Firmware hien tai cua loa: %fwv%
  echo     Cong cu nay CHI ho tro phien ban firmware 3448.
  echo     Neu cai tren phien ban khac co the bi:
  echo     - Khong danh thuc duoc bang giong noi [mat wake word]
  echo     - Mat tieng [khong co am thanh]
  echo     - Khong the mo Web Console [cong 8080].
  echo.
  echo     -^> Vui long nang cap firmware loa len 3448 truoc,
  echo         sau do chay lai file cai dat nay.
  echo     -^> Hien tai ban chi co the dung cac chuc nang phu tu [4] den [9].
  echo  ----------------------------------------------------------
  echo.
  if not defined ARGDO pause
  color 0a
)

:menu
echo.
echo  ==================== MENU CHUC NANG ====================
echo   [1] Cai dat nhanh toan bo [so host + goi chinh + console + khoi dong] [KHUYEN NGHI]
echo   [2] Chi cai / Cai lai so host       [XiaoZhi_SoHost_V2.0.1.apk]
echo   [3] Chi cai / Nang cap goi chinh     [XiaoZhi_Unisound_V2.0.6.apk]
echo   [4] Chi cai / Nang cap goi console   [XiaoZhi_EchoService_V2.0.6.apk]
echo   [5] Mo Web Console [:8080]
echo   [6] Cai dat dia chi Server [Chinh thuc / May chu noi bo tu dung]
echo   [7] Khoi dong lai XiaoZhi           [8] Khoi dong lai loa [Reboot]
echo   [9] Xem log hoat dong tren loa     [s] Mo adb shell
echo   [v] Kiem tra phien ban [Phien ban file apk + Thoi gian cai dat tren loa]
echo   [0] Thoat
echo  ========================================================
if defined ARGDO (
  set "sel=%ARGDO%"
  echo  Che do dong lenh, tu dong thuc thi [!sel!]
) else (
  set "sel="
  set /p "sel=Nhap lua chon cua ban roi nhan Enter : "
)
if /i "%sel%"=="1" goto do_all
if /i "%sel%"=="2" goto do_pure
if /i "%sel%"=="3" goto do_jp2
if /i "%sel%"=="4" goto do_pl
if /i "%sel%"=="5" goto do_web
if /i "%sel%"=="6" goto do_server
if /i "%sel%"=="7" goto do_restart
if /i "%sel%"=="8" goto do_reboot
if /i "%sel%"=="9" goto do_log
if /i "%sel%"=="s" goto do_shell
if /i "%sel%"=="v" goto do_ver
if /i "%sel%"=="0" goto bye
echo  [LOI] Lua chon khong hop le, vui long nhap 0~9 hoac s, v.
if defined ARGDO exit /b 3
goto menu

:lockchk
if "%FWOK%"=="1" exit /b 0
color 04
echo.
echo  ############ TU CHOI CAI DAT ############
echo   Phien ban firmware hien tai %fwv% khong phai 3448!
echo   Cong cu nay khong cho phep cai tren phien ban nay de tranh loi he thong.
echo   Vui long nang cap firmware loa len 3448 truoc roi quay lai cai dat.
echo  ##########################################
echo.
if not defined ARGDO pause
color 0a
exit /b 1

:do_all
echo.
echo  ============== BAT DAU CAI DAT (KHOANG 2 PHUT) ==============
call :lockchk
if errorlevel 1 goto failop
call :spacechk
call :install_pure
if errorlevel 1 goto failop
call :install_jp2
if errorlevel 1 goto failop
call :install_pl
if errorlevel 1 goto failop
call :start_app
call :start_pl
call :waitweb
if errorlevel 1 goto failop
call :showver
echo.
echo  ============== CAI DAT HOAN TAT ==============
echo  Web Console: http://%DEVIP%:8080/
echo.
echo  Cac buoc tiep theo:
echo  1. Mo trinh duyet truy cap Web Console http://%DEVIP%:8080/
echo  2. O the "Kich hoat" tren dau trang, lay ma 6 chu so -^> Nhap vao server de lien ket loa.
echo  3. Neu dung server tu dung (local), dung menu [6] de nhap lai dia chi server (cai moi se xoa OTA cu).
echo.
if not defined ARGDO (
  set "ow="
  set /p "ow=Co muon mo Web Console ngay bay gio khong? [y/n] : "
  if /i "!ow!"=="y" start http://%DEVIP%:8080/
  pause
)
goto done

:do_pure
echo.
call :lockchk
if errorlevel 1 goto failop
call :install_pure
goto done

:do_jp2
echo.
call :lockchk
if errorlevel 1 goto failop
call :install_jp2
if errorlevel 1 goto failop
call :install_pl
if errorlevel 1 goto failop
call :start_app
call :start_pl
call :waitweb
if errorlevel 1 goto failop
goto done

:do_pl
echo.
call :lockchk
if errorlevel 1 goto failop
call :install_pl
if errorlevel 1 goto failop
call :start_pl
call :waitweb
goto done

:do_web
start http://%DEVIP%:8080/
goto menu

:do_restart
echo.
echo  Dang khoi dong lai XiaoZhi [tien trinh device khoi dong lai, giu nguyen cau hinh]...
"%ADB%" %type% shell am force-stop com.phicomm.speaker.device <nul
"%ADB%" %type% shell am start -n com.phicomm.speaker.device/.ui.MainActivity <nul
echo  [OK] Da khoi dong lai. Vui long doi khoang 10 giay roi lam moi lai trang Web Console.
goto done

:do_reboot
echo.
echo  Dang khoi dong lai loa Phicomm R1 ...
"%ADB%" %type% reboot <nul
echo  [OK] Lenh khoi dong lai da duoc gui toi loa.
goto done

:do_log
echo.
echo  ------------ LOG HOAT DONG GAN NHAT [r1client.log] ------------
"%ADB%" %type% shell "run-as com.phicomm.speaker.device cat files/r1client.log" <nul
echo  ----------------------------------------------------------------
goto done

:do_shell
echo.
echo  Dang vao adb shell [nhap 'exit' va nhan Enter de quay lai menu nay]
"%ADB%" %type% shell
goto menu

:failop
echo.
echo  [LOI] Thao tac truoc do that bai, tien trinh dung lai.
goto done

:done
if defined ARGDO goto bye
pause
goto menu

:bye
echo.
echo  Hoan tat thao tac.
echo  Tu khoa danh thuc mac dinh la "XiaoZhi XiaoZhi" [Tieu Tri], sau khi goi hay doi tieng bip roi moi noi.
if not defined ARGDO pause
exit /b 0

rem ==================== CAC HAM CON ====================

:spacechk
echo.
echo  ---- Kiem tra dung luong bo nho /data tren loa [Can trong tren 300MB de cai dat] ----
"%ADB%" %type% shell df /data <nul
exit /b 0

:showver
echo.
echo  ---- Phien ban file bo cai [Doc tu ten file APK] ----
if defined JPV  (echo   device [XiaoZhi goc] : V%JPV%)  else (echo   device [XiaoZhi goc] : Khong tim thay file APK)
if defined PLV  (echo   player [Console Web] : pl-%PLV%) else (echo   player [Console Web] : Khong tim thay file APK)
echo  ---- Thoi gian cai dat thuc te tren loa ----
"%ADB%" %type% shell dumpsys package com.phicomm.speaker.device > "%TEMP%1_pk1.txt" 2>nul <nul
set "LT1="
for /f "tokens=2 delims==" %%a in ('findstr /c:"lastUpdateTime" "%TEMP%1_pk1.txt"') do if not defined LT1 set "LT1=%%a"
"%ADB%" %type% shell dumpsys package com.phicomm.speaker.player > "%TEMP%1_pk2.txt" 2>nul <nul
set "LT2="
for /f "tokens=2 delims==" %%a in ('findstr /c:"lastUpdateTime" "%TEMP%1_pk2.txt"') do if not defined LT2 set "LT2=%%a"
echo   device [XiaoZhi goc] : %LT1%
echo   player [Console Web] : %LT2%
echo   [Thoi gian phai la vua xong; neu khong khop tuc la chua cai dat thanh cong vao loa]
exit /b 0

:do_ver
echo.
echo  ------------ DOI CHIEU PHIEN BAN ------------
call :showver
goto done

:waitweb
echo.
echo  Dang cho Web Console khoi dong san sang [binh thuong 2~5 giay, khoi dong lan dau mat 15~40 giay]...
set "WT=0"

:waitweb_loop
"%ADB%" %type% shell cat /proc/net/tcp6 > "%TEMP%1_tcp6.txt" 2>nul <nul
findstr /r /c:":1F90 00000000000000000000000000000000:0000 0A" "%TEMP%1_tcp6.txt" >nul 2>&1
if not errorlevel 1 goto waitweb_ok
"%ADB%" %type% shell cat /proc/net/tcp > "%TEMP%1_tcp.txt" 2>nul <nul
findstr /r /c:":1F90 00000000:0000 0A" "%TEMP%1_tcp.txt" >nul 2>&1
if not errorlevel 1 goto waitweb_ok

ping -n 4 127.0.0.1 >nul
set /a WT+=3
if !WT! GEQ 60 (
  echo.
  echo  [CANH BAO] Da cho 60 giay nhung Web Console van chua san sang!
  echo  Dung voi rut nguon loa, hay kiem tra va xu ly theo cac buoc sau:
  echo    1. Chon menu [9] de xem log, kiem tra xem tien trinh player da khoi dong chua.
  echo    2. Chon menu [4] de cai dat lai rieng goi console [player].
  echo    3. Neu van khong duoc, chon menu [8] de khoi dong lai loa.
  exit /b 1
)
echo    ... Da cho !WT! giay
goto waitweb_loop

:waitweb_ok
echo  [OK] Web Console da san sang [cho mat !WT! giay].
exit /b 0

:install_pure
if not defined PURE (
  color 04
  echo  [LOI] Khong tim thay XiaoZhi_SoHost_V*.apk!
  pause
  color 0a
  exit /b 1
)
echo  [1/3] Go bo so host cu [lan dau cai neu bao Failure la hoan toan binh thuong]...
"%ADB%" %type% shell "sh -c 'pm uninstall cn.workbuddy.r1client > /data/local/tmp/un.txt 2>&1'" <nul
"%ADB%" %type% shell "sh -c 'pm unhide cn.workbuddy.r1client > /dev/null 2>&1'" <nul
echo  [2/3] Dang tai len %PURE% len loa...
call :pushfile "%PURE%" r1pure.apk
if errorlevel 1 exit /b 1
echo  [3/3] Dang cai dat so host...
call :pmi r1pure.apk ""
if errorlevel 1 exit /b 1
echo  [OK] Cai dat so host thanh cong.
exit /b 0

:install_jp2
if not defined JP2 (
  color 04
  echo  [LOI] Khong tim thay XiaoZhi_Unisound_V*.apk!
  pause
  color 0a
  exit /b 1
)
echo  Dang dung tien trinh r1client cu [tranh chiem dung micro]...
"%ADB%" %type% shell am force-stop cn.workbuddy.r1client <nul
echo  [1/2] Dang tai len %JP2% len loa...
call :pushfile "%JP2%" jp2.apk
if errorlevel 1 exit /b 1
echo  [2/2] Dang cai dat goi chinh...
call :pmi jp2.apk "-r"
if errorlevel 1 exit /b 1
echo  [OK] Cai dat goi chinh thanh cong.
exit /b 0

:start_app
echo  Dang khoi dong XiaoZhi tren loa...
"%ADB%" %type% shell am force-stop cn.workbuddy.r1client <nul
"%ADB%" %type% shell am start -n com.phicomm.speaker.device/.ui.MainActivity <nul
echo  [OK] Da khoi dong xong. Cho 10~15 giay sau do truy cap http://%DEVIP%:8080/
exit /b 0

:install_pl
if not defined PLYR (
  color 04
  echo  [LOI] Khong tim thay XiaoZhi_EchoService_V*.apk!
  pause
  color 0a
  exit /b 1
)
echo  [1/2] Dang tai len %PLYR% len loa...
call :pushfile "%PLYR%" echo.apk
if errorlevel 1 exit /b 1
echo  [2/2] Dang cai dat goi console [player]...
call :pmi echo.apk "-r"
if errorlevel 1 exit /b 1
echo  [OK] Cai dat goi console thanh cong.
exit /b 0

:start_pl
echo  Dang khoi dong dich vu console [player]...
"%ADB%" %type% shell am startservice -n com.phicomm.speaker.player/.EchoService <nul
exit /b 0

:pushfile
set "LF=%~1"
set "RN=%~2"
set "LZ=%~z1"
set "RS="
"%ADB%" %type% shell ls -l /data/local/tmp/%RN% > "%TEMP%1_sz.txt" 2>nul <nul
for /f "tokens=4" %%s in (%TEMP%1_sz.txt) do set "RS=%%s"
if "%RS%"=="%LZ%" (
  echo  -^> File tren loa da co san va cung dung luong, bo qua buoc tai len.
  exit /b 0
)
"%ADB%" %type% push "%LF%" /data/local/tmp/%RN% <nul
if errorlevel 1 (
  color 04
  echo  [LOI] Tai file len loa that bai!
  pause
  color 0a
  exit /b 1
)
set "RS="
"%ADB%" %type% shell ls -l /data/local/tmp/%RN% > "%TEMP%1_sz.txt" 2>nul <nul
for /f "tokens=4" %%s in (%TEMP%1_sz.txt) do set "RS=%%s"
if not "%RS%"=="%LZ%" (
  color 04
  echo  [LOI] File tai len khong tron ven! May tinh: %LZ% bytes, Tren loa: %RS% bytes.
  echo  Ket noi WiFi ADB co the bi rot goi hoac gian doan. Vui long ket noi lai va thu lai.
  pause
  color 0a
  exit /b 1
)
echo  -^> Tai len hoan tat [%LZ% bytes, da xac thuc dung luong].
exit /b 0

:pmi
set "RN=%~1"
set "FLG=%~2"
echo  Dang cai dat [Thiet bi thuc hien toi uu dexopt mat 30~90 giay, xin kien nhan cho]...
"%ADB%" %type% shell "sh -c '/system/bin/pm install %FLG% /data/local/tmp/%RN% > /data/local/tmp/inst.txt 2>&1'" <nul
"%ADB%" %type% shell cat /data/local/tmp/inst.txt > "%~dp0inst.txt" <nul
type "%~dp0inst.txt"
findstr /i /c:"Success" "%~dp0inst.txt" >nul
if errorlevel 1 (
  color 04
  echo.
  echo  [LOI] CAI DAT THAT BAI! Chi tiet nguyen nhan tra ve tu loa o tren.
  echo  Cac loi thuong gap va cach xu ly:
  echo    - INSTALL_FAILED_UPDATE_INCOMPATIBLE : Goi cu khac chu ky [signature], can go bo goi cu roi cai lai.
  echo    - INSTALL_PARSE_FAILED_*             : File APK bi loi hoac upload bi gian doan, hay thu lai.
  echo    - INSTALL_FAILED_DEXOPT              : Bo nho trong /data cua loa bi day khong du dung luong.
  pause
  color 0a
  exit /b 1
)
if exist "%~dp0inst.txt" del "%~dp0inst.txt" >nul
echo  [OK] Cai dat vao he thong thanh cong.
exit /b 0

:do_server
echo.
echo  ------------ CAI DAT DIA CHI SERVER ------------
if defined ARGSRV (
  set "SIP=%ARGSRV%"
) else (
  echo  Dung server chinh thuc nhap 'official'; Dung server noi bo [local] hay nhap IP cua server.
  set /p "SIP=IP Server [Mac dinh 192.168.2.189]: "
  if not defined SIP set "SIP=192.168.2.189"
)
if /i "%SIP%"=="official" (
  set "OTA="
  set "WSU="
  echo  Se xoa cau hinh tuy chinh, quay ve server mac dinh cua hang.
) else (
  set "OTA=http://%SIP%:8003/xiaozhi/ota/"
  set "WSU="
  if not defined ARGSRV (
    set "WSQ="
    set /p "WSQ=Co muon dien them dia chi WS truc tiep ws://%SIP%:8000/xiaozhi/v1/ khong? [y/n, thuong chon n] : "
    if /i "!WSQ!"=="y" set "WSU=ws://%SIP%:8000/xiaozhi/v1/"
  )
)
echo  OTA = %OTA%
echo  WS  = %WSU%
echo.
where curl.exe >nul 2>nul
if errorlevel 1 goto server_adb
curl.exe -s --noproxy "*" -m 10 -X POST -d "otaUrl=%OTA%&wsUrl=%WSU%" http://%DEVIP%:8080/api/set
echo.
echo  [OK] Da gui cau hinh! Hay kiem tra the 'Server' tren Web Console hoac xem log de xac nhan dia chi ws.
goto done

:server_adb
echo  Khong tim thay curl.exe, chuyen sang dung adb de ghi truc tiep file cau hinh [hieu qua tuong duong]...
if defined OTA (
  "%ADB%" %type% shell "run-as com.phicomm.speaker.device sh -c 'echo %OTA% > files/ota_url'" <nul
) else (
  "%ADB%" %type% shell "run-as com.phicomm.speaker.device sh -c 'rm -f files/ota_url'" <nul
)
echo  [OK] Da ghi vao files/ota_url. Vui long dung menu [7] de khoi dong lai XiaoZhi de ap dung thay doi.
goto done
