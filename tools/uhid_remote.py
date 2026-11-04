#!/usr/bin/env python3
"""
UHID remote control for Xiaomi 18 Fold (HyperOS).
Creates a virtual HID touchscreen via /dev/uhid (kernel-level, bypasses INJECT_EVENTS).

Usage:
  python3 uhid_remote.py tap X Y
  python3 uhid_remote.py swipe X1 Y1 X2 Y2 [DUR_MS]
  python3 uhid_remote.py hold X Y MS
"""
import struct
import subprocess
import sys
import time

SN = "06861C7049983540"
SCREEN_W, SCREEN_H = 1672, 2364
ABS_MAX = 32767

UHID_DESTROY2 = 1
UHID_CREATE2 = 11
UHID_INPUT2 = 12

BUS_USB = 0x03

# Single-touch digitizer (touchscreen), B protocol, with Contact Count Maximum.
# This is the proven format from STM32/Android multitouch references that
# Android InputReader accepts as a TOUCHSCREEN.
# Report layout (5 bytes): [report_id:8][contact_id+tip:8][x:16le][y:16le]
REPORT_DESC = bytes([
    0x05, 0x0D,        # Usage Page (Digitizers)
    0x09, 0x04,        #   Usage (Touch Screen)
    0xA1, 0x01,        #   Collection (Application)
    0x85, 0x01,        #     Report ID (1)
    0x09, 0x54,        #     Usage (Contact Count)
    0x95, 0x01,        #     Report Count (1)
    0x75, 0x08,        #     Report Size (8)
    0x15, 0x00,        #     Logical Minimum (0)
    0x25, 0x01,        #     Logical Maximum (1)
    0xB1, 0x02,        #     Feature (Data,Var,Abs)   -- Contact Count Maximum
    0x09, 0x22,        #     Usage (Finger)
    0xA1, 0x02,        #     Collection (Logical)
    0x09, 0x51,        #       Usage (Contact Identifier)
    0x15, 0x00,        #       Logical Minimum (0)
    0x25, 0x01,        #       Logical Maximum (1)
    0x75, 0x01,        #       Report Size (1)
    0x95, 0x01,        #       Report Count (1)
    0x81, 0x02,        #       Input (Data,Var,Abs)    -- contact id (bit0)
    0x09, 0x42,        #       Usage (Tip Switch)
    0x75, 0x01,        #       Report Size (1)
    0x95, 0x01,        #       Report Count (1)
    0x81, 0x02,        #       Input (Data,Var,Abs)    -- tip switch (bit1)
    0x75, 0x06,        #       Report Size (6)
    0x95, 0x01,        #       Report Count (1)
    0x81, 0x03,        #       Input (Cnst,Var,Abs)    -- padding (6 bits)
    0x05, 0x01,        #     Usage Page (Generic Desktop)
    0x09, 0x30,        #       Usage (X)
    0x09, 0x31,        #       Usage (Y)
    0x16, 0x00, 0x00,  #       Logical Minimum (0)
    0x26, 0xFF, 0x7F,  #       Logical Maximum (32767)
    0x75, 0x10,        #       Report Size (16)
    0x95, 0x02,        #       Report Count (2)
    0x81, 0x02,        #       Input (Data,Var,Abs)    -- X, Y
    0xC0,              #     End Collection (Logical)
    0xC0,              #   End Collection (Application)
])


def pack_create2():
    name = b"FoldDeck UHID Touch".ljust(128, b"\x00")
    phys = b"usb".ljust(64, b"\x00")
    uniq = b"folddeck01".ljust(64, b"\x00")
    rd = REPORT_DESC
    body = (
        name + phys + uniq
        + struct.pack("<HH", len(rd), BUS_USB)   # rd_size, bus
        + struct.pack("<IIII", 0x1234, 0x5678, 0x0001, 0)  # vendor,product,version,country
        + rd.ljust(4096, b"\x00")
    )
    return struct.pack("<I", UHID_CREATE2) + body


def pack_input2(report: bytes):
    return struct.pack("<IH", UHID_INPUT2, len(report)) + report


def pack_destroy2():
    # struct uhid_destroy2_req is empty; event = type only (4 bytes)
    return struct.pack("<I", UHID_DESTROY2)


def touch_report(down: bool, x: int, y: int):
    ax = max(0, min(ABS_MAX, int(x * ABS_MAX / SCREEN_W)))
    ay = max(0, min(ABS_MAX, int(y * ABS_MAX / SCREEN_H)))
    # report id 1 + contact byte: bit0=contact identifier, bit1=tip switch
    contact = 0x00
    if down:
        contact = 0x01 | 0x02   # bit0 = contact id present, bit1 = tip switch down
    return struct.pack("<BBHH", 0x01, contact, ax, ay)


class Uhid:
    def __init__(self):
        self.proc = subprocess.Popen(
            ["adb", "-s", SN, "exec-in", "cat > /dev/uhid"],
            stdin=subprocess.PIPE,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        self.send(pack_create2())
        time.sleep(0.4)  # let kernel enumerate device

    def send(self, data: bytes):
        self.proc.stdin.write(data)
        self.proc.stdin.flush()

    def close(self):
        try:
            self.send(pack_destroy2())
        except Exception:
            pass
        try:
            self.proc.stdin.close()
            self.proc.terminate()
        except Exception:
            pass


def main():
    cmd = sys.argv[1] if len(sys.argv) > 1 else "tap"
    uh = Uhid()
    try:
        if cmd == "tap":
            x, y = int(sys.argv[2]), int(sys.argv[3])
            uh.send(pack_input2(touch_report(True, x, y)))
            time.sleep(0.08)
            uh.send(pack_input2(touch_report(False, x, y)))
            print(f"TAP_OK {x},{y}")
        elif cmd == "swipe":
            x1, y1 = int(sys.argv[2]), int(sys.argv[3])
            x2, y2 = int(sys.argv[4]), int(sys.argv[5])
            dur = int(sys.argv[6]) if len(sys.argv) > 6 else 400
            uh.send(pack_input2(touch_report(True, x1, y1)))
            steps = max(dur // 15, 2)
            for i in range(1, steps + 1):
                t = i / steps
                uh.send(pack_input2(touch_report(True,
                    int(x1 + (x2 - x1) * t), int(y1 + (y2 - y1) * t))))
                time.sleep(dur / steps / 1000.0)
            uh.send(pack_input2(touch_report(False, x2, y2)))
            print(f"SWIPE_OK {x1},{y1}->{x2},{y2}")
        elif cmd == "hold":
            x, y = int(sys.argv[2]), int(sys.argv[3])
            ms = int(sys.argv[4])
            uh.send(pack_input2(touch_report(True, x, y)))
            time.sleep(ms / 1000.0)
            uh.send(pack_input2(touch_report(False, x, y)))
            print(f"HOLD_OK {x},{y} {ms}ms")
        else:
            print(f"unknown cmd {cmd}")
        time.sleep(0.3)  # let events drain before device teardown
    finally:
        uh.close()


if __name__ == "__main__":
    main()
