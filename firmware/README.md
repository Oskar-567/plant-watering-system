# Plant Watering Firmware (ESP32)

Firmware for the ESP32 that drives the pump, reads the sensors and talks to
the server over MQTT. Hardware details and the module structure are in
`CLAUDE.md`; the design documents live in `docs/superpowers/specs/`.

## Build, test, flash

```bash
pio test -e native            # unit tests for the pure logic (no hardware)
pio run  -e esp32dev          # compile
pio run  -e esp32dev -t upload   # flash over WiFi (OTA)
```

`upload_protocol = espota`, so a flash needs no cable. The target address and
the OTA password are machine-specific and live in `platformio_override.ini`
(gitignored) — copy `platformio_override.ini.example` and fill it in. We
address the ESP32 by LAN IP rather than `plant-esp32.local`, because mDNS
proved unreliable on Windows.

Cables are still needed in one case: if a flashed build crashes before WiFi
comes up, OTA is gone with it and the board has to be recovered over USB.
When flashing over USB, disconnect the battery/CN3791 from the VIN net first
— see the warning in `CLAUDE.md`.

## Talking to the broker

The broker is Mosquitto on the homelab k3s cluster, reachable on the LAN at
`192.168.73.150:31883`, user `plant`. The commands below assume the
mosquitto clients are installed (`winget install EclipseFoundation.Mosquitto`
on Windows); [MQTT Explorer](http://mqtt-explorer.com/) is a fine GUI
alternative for browsing topics.

To keep the password out of your shell history, put it in an environment
variable once per session instead of typing it into every command:

```bash
export MQTT_PW='...'          # PowerShell: $env:MQTT_PW = '...'
export MQTT='-h 192.168.73.150 -p 31883 -u plant -P '"$MQTT_PW"
```

### Watch everything the device sends

```bash
mosquitto_sub $MQTT -t 'plant/#' -v
```

`-v` prints the topic next to the payload, which you want as soon as more
than one topic is in play.

### Remote debug log

The live stream, and the lines recovered from before the last reset:

```bash
mosquitto_sub $MQTT -t 'plant/log' -v
mosquitto_sub $MQTT -t 'plant/log/history' -v
```

`plant/log/history` is published once per reconnect, and only when there is
something to replay. These are the lines held in RTC memory when the device
reset — a brownout while pumping, a watchdog reboot or a panic. They are what
the live stream cannot show you, because the link was already down. After a
true power loss (flat cell) the RTC contents are gone and nothing is replayed;
the `reset_reason` on `plant/diag` then reads `poweron`.

The device logs at `info` by default to save radio time — the cell is solar
charged and a chatty log measurably costs runtime. Raise it when you need
detail:

```bash
mosquitto_pub $MQTT -t plant/debug/command -m '{"level":"debug","minutes":30}'
mosquitto_pub $MQTT -t plant/debug/command -m '{"level":"info"}'
```

Levels are `error`, `warn`, `info`, `debug`, lower case and matched exactly —
a typo is rejected rather than silently applied, and the device confirms every
accepted change on `plant/log`. A raised level always expires on its own
(default 15 minutes, capped at 120), so a forgotten `debug` cannot drain the
battery. `info` is the resting level and never expires.

### Reboot the device

```bash
mosquitto_pub $MQTT -t plant/debug/command -m '{"action":"reboot"}'
```

`esp_restart()` is a software reset, so the RTC log ring survives it and the
lines from before the restart come back on `plant/log/history`. That makes
this the cheap way to exercise the post-mortem path — no OTA cycle needed —
and the way to wake a wedged device without waiting out the watchdog.

Refused while the pump is running: a reset mid-run would abandon the flow
count and the server would never see the matching `off`. The refusal is
logged, so you can tell it apart from a command that never arrived. A
power cycle is *not* equivalent — it clears RTC memory, so no history is
replayed and `plant/diag` reports `reset_reason: poweron`.

### Test sleep (measure the sleep current)

```bash
mosquitto_pub $MQTT -t plant/debug/command -m '{"action":"sleep","s":120}'
```

One deep sleep of `s` seconds (default 60, capped at 600), then a normal boot
(`plant/diag` reports `reset_reason: deepsleep`), so OTA keeps working and no
USB is needed. GPIO 26 is held low during the sleep. Refused while the pump is
running, like the reboot.

Measuring:

1. Disconnect the solar panel. Put the meter in the battery plus lead, ahead
   of the whole system.
2. **Bridge the meter with a wire while the device is awake.** In the mA/µA
   range its shunt drops too much voltage, and the WiFi peaks (300–500 mA)
   would brown the ESP32 out.
3. Send the command, wait until the device sleeps, remove the bridge and read.
   Bridge again before the sleep ends.
4. Unplug one consumer at a time (moisture sensor VCC, MOSFET module, boost
   input) and repeat. Each difference is that part's share.

Above about 1 mA in sleep, deep sleep cannot fix the energy budget; see
`docs/superpowers/specs/2026-10-02-esp32-deep-sleep-v1-design.md`, step 0.

### Pump

```bash
mosquitto_pub $MQTT -t plant/pump/command -m '{"action":"start"}'
mosquitto_pub $MQTT -t plant/pump/command -m '{"action":"stop"}'
mosquitto_sub $MQTT -t 'plant/status' -v
```

The pump refuses to start on a low cell and stops by itself on max runtime,
flow stall, voltage sag or a lost link — see the pump row in `CLAUDE.md` for
the thresholds. Every stop reports its reason on `plant/status` and
`plant/diag`.

### Health after an outage

```bash
mosquitto_sub $MQTT -t 'plant/diag' -v
```

Published on every MQTT reconnect: reset reason, WiFi drop count and reason,
RSSI and cell voltage. This is the first place to look when the device has
been quiet — together with `plant/log/history` it usually explains an outage
without touching the hardware.
