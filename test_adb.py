import socket
import struct

def send_msg(sock, cmd, arg0, arg1, data):
    cmd_bytes = cmd.encode('ascii')
    msg = struct.pack('<4sIIII', cmd_bytes, arg0, arg1, len(data), sum(data))
    magic = bytes([~b & 0xFF for b in cmd_bytes])
    sock.sendall(msg + magic)
    if data:
        sock.sendall(data)

def read_msg(sock):
    header = sock.recv(24)
    if len(header) < 24: return None, None
    cmd, arg0, arg1, length, crc, magic = struct.unpack('<4sIIIII', header)
    data = b''
    while len(data) < length:
        chunk = sock.recv(length - len(data))
        if not chunk: break
        data += chunk
    return cmd.decode('ascii'), data

sock = socket.socket()
sock.connect(('127.0.0.1', 5555))

# Send CNXN
send_msg(sock, 'CNXN', 0x01000000, 4096, b'host::')
cmd, data = read_msg(sock)
print("Recv:", cmd, data)

# Send OPEN
send_msg(sock, 'OPEN', 1, 0, b'shell:echo "7fff 0000ff" > /sys/class/leds/multi_leds0/led_color\0')
while True:
    cmd, data = read_msg(sock)
    print("Recv:", cmd, data)
    if cmd in ('CLSE', None): break

sock.close()
