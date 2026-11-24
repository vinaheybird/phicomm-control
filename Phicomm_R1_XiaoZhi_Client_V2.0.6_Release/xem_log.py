import subprocess
import sys
import os
import re
import json

# Setup UTF-8 console output for Windows
if sys.platform == 'win32':
    import io
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace', line_buffering=True)

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
ADB = os.path.join(BASE_DIR, 'adb.exe')
DEVICE = '192.168.88.66:5555'

print("=" * 65)
print("  THEO DÕI LOG NHẬN DIỆN VÀ TRÒ CHUYỆN THỜI GIAN THỰC")
print(f"  Đang kết nối tới loa: {DEVICE} ...")
print("=" * 65)

subprocess.run([ADB, 'connect', DEVICE], stdout=subprocess.PIPE, stderr=subprocess.PIPE, stdin=subprocess.DEVNULL)

cmd = [ADB, '-s', DEVICE, 'logcat', '-v', 'threadtime']
proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, stdin=subprocess.DEVNULL)

print("Đã kết nối! Bạn hãy thử gọi 'Xẻo Chư Xẻo Chư' hoặc nói chuyện với loa.")
print("Bấm Ctrl + C để thoát.\n")
print("-" * 65)

expect_score = False

try:
    for raw_line in proc.stdout:
        line = raw_line.decode('utf-8', errors='ignore').strip()

        # Catch wake recognition line
        if 'FixRecognitionThread:partial' in line and '小智小智' in line:
            expect_score = True
            continue

        if expect_score:
            expect_score = False
            # Line is often ": -11.81" or similar
            m = re.search(r'USC\s*:\s*(-?\d+\.\d+)', line)
            if m:
                score = float(m.group(1))
                if score >= -10.0:
                    print(f"\n[🎙️ GỌI LOA] Điểm số: {score:+.2f}  ==>  ✅ THÀNH CÔNG! (Loa kích hoạt thật sự)")
                elif score >= -15.0:
                    print(f"\n[🎙️ GỌI LOA] Điểm số: {score:+.2f}  ==>  ⚠️ SUÝT TRÚNG! (Cần > -10.0, hãy nói to và dứt khoát hơn chút nữa)")
                else:
                    print(f"\n[🎙️ GỌI LOA] Điểm số: {score:+.2f}  ==>  ❌ CHƯA ĐẠT (Cần > -10.0, sai âm điệu)")
                continue

        # Catch real wakeup success
        if 'onWakeupSuccess' in line or 'wakeup src=' in line:
            print("[⚡ ĐÁNH THỨC] Loa đã bật đèn tròn và đang mở micro lắng nghe...")
            continue

        # Catch STT (user speech)
        if 'type":"stt"' in line:
            m = re.search(r'\{"type":"stt".*?\}', line)
            if m:
                try:
                    data = json.loads(m.group(0))
                    text = data.get('text', '')
                    if text:
                        print(f"[👤 BẠN NÓI]: \"{text}\"")
                except:
                    pass
            continue

        # Catch TTS (XiaoZhi response)
        if 'type":"tts","state":"sentence_start"' in line:
            m = re.search(r'\{"type":"tts".*?\}', line)
            if m:
                try:
                    data = json.loads(m.group(0))
                    text = data.get('text', '')
                    if text:
                        print(f"[🤖 XIAOZHI]: \"{text}\"")
                except:
                    pass
            continue

        # Catch Session Quit / Idle
        if 'mcp call self.r1session.quit' in line or 'back to IDLE' in line:
            print("[💤 NGHỈ NGƠI] Loa tắt đèn và quay về chế độ chờ (IDLE).\n" + "-" * 65)
            continue

except KeyboardInterrupt:
    proc.terminate()
    print("\nĐã dừng theo dõi log.")
