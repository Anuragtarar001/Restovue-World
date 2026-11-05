# Upload signing

No key was generated and no secret is stored in this repository.

For an owner-authorized upload key, set these environment variables outside source control:

- `RESTOVUE_UPLOAD_STORE_FILE`
- `RESTOVUE_UPLOAD_STORE_PASSWORD`
- `RESTOVUE_UPLOAD_KEY_ALIAS`
- `RESTOVUE_UPLOAD_KEY_PASSWORD`

Then run `gradlew.bat bundleRelease`. Without all four variables, Gradle intentionally produces an unsigned release bundle. Enroll the upload certificate in Google Play App Signing through the owner-controlled Play Console. Never use the debug key for a Play release.
