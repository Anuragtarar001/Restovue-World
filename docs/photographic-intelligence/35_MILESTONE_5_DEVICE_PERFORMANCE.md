# Milestone 5 Device Performance

Device: Samsung SM-S926B, Android API reported by the connected test target as `SM-S926B - 17`.
The final targeted instrumentation run passed 2/2 tests in 172.656 seconds. That total includes four
images, twelve production renders, semantic initialization/inference, PNG originals/renders/difference
maps/overlays, JSON reports, a contact sheet, and a MediaStore JPEG parity export; it is not preview
latency.

Final per-render times from execution traces (ms):

| Fixture | Natural | Enhanced | Extreme |
|---|---:|---:|---:|
| Cream/background local contrast | 12,787 | 20,782 | 20,785 |
| Backlit person | 5,818 | 6,582 | 6,527 |
| Group person | 7,050 | 7,060 | 7,069 |
| Public-domain no-op | 946 | 945 | 943 |

The neighborhood operator is CPU-bound and currently too slow for ideal interactive full-resolution
preview; production UI uses bounded preview resolution and background dispatch. Managed/native peak
memory and thermal drain were not captured by a reliable automated profiler and are NOT TESTED.

The full connected suite additionally measured a 777,600-pixel bounded preview at **2,953 ms**, a
12 MP production export reusing the selected session/plan at **26,503 ms**, and a separate 12 MP
reopenable gallery-export test at **39,626 ms**. The latter recorded managed heap from 108,761,824
bytes before to 243,720,928 bytes after against a 268,435,456-byte maximum; this is not a peak-native
memory measurement and confirms why 48 MP full-frame processing remains safely blocked.

