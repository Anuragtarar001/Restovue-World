# Google Play Data Safety worksheet

Code-audit basis: release candidate 0.9.0-rc1. This is a preparation worksheet, not a submitted declaration.

| Question | Audited answer | Evidence / owner check |
|---|---|---|
| Data collected by the app developer | None identified | No INTERNET permission, accounts, analytics, ads, telemetry or upload code. |
| Data shared with third parties | None identified by the app | External browser link is user-initiated; owner must ensure the website policy is consistent. |
| Photos/videos | User-selected and processed on device | Photo Picker URIs; no broad storage permission or upload path. |
| Files/documents | User-selected destinations | Storage Access Framework. |
| Local presets/LUT references | Stored locally for app functionality | App-private files; removable by deletion or clearing app data. |
| Advertising ID or device identifiers | Not collected | No ad/analytics SDK or identifier API found. |
| Encryption in transit | Not applicable to app data flow | No network permission. External browser traffic is outside the app. |
| User deletion | Local deletion available | Delete presets/exports or clear/uninstall app data. |

Owner must re-audit the final signed artifact and answer Play Console wording exactly as presented at submission time.
