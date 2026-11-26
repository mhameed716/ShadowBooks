# Permanent signing key (encrypted)

`shadowbooks-release.p12.enc` is the ShadowBooks Android signing keystore
(PKCS#12, alias `shadowbooks`), encrypted with AES-256-CBC + PBKDF2.

* The **only** copy of the password lives in the GitHub Actions secret
  `SHADOWBOOKS_SIGNING_PASSWORD`. The same password unlocks the encrypted file
  and the keystore inside it.
* CI (`.github/workflows/build-android.yml`) decrypts it to a temporary file and
  points Gradle at it via `SHADOWBOOKS_KEYSTORE_FILE` / `SHADOWBOOKS_KEYSTORE_PASSWORD`
  / `SHADOWBOOKS_KEY_ALIAS` / `SHADOWBOOKS_KEY_PASSWORD`. Nothing unencrypted is
  ever committed.
* Certificate SHA-256 fingerprint:
  `79:73:0F:B9:14:A0:C7:FE:DB:9E:5E:9E:83:B3:D7:BE:90:6D:A9:F7:9B:C9:CA:D6:57:5A:FA:9C:5A:44:54:CA`
  (valid until 2056). Every published build must show this same fingerprint —
  that is what lets phones install each release as an update over the previous one.

## If the password is ever lost

Generate a new key and re-encrypt (this resets update compatibility one more
time, so keep the password in a password manager):

```bash
openssl req -x509 -newkey rsa:2048 -keyout key.pem -out cert.pem -days 10950 -nodes \
  -subj "/CN=ShadowBooks/O=ShadowBooks/C=YE"
openssl pkcs12 -export -out shadowbooks-release.p12 -inkey key.pem -in cert.pem \
  -name shadowbooks -passout pass:<NEW_PASSWORD>
openssl enc -aes-256-cbc -salt -pbkdf2 -in shadowbooks-release.p12 \
  -out shadowbooks-release.p12.enc -pass pass:<NEW_PASSWORD>
```

Then update the `SHADOWBOOKS_SIGNING_PASSWORD` secret to `<NEW_PASSWORD>` and
replace the fingerprint above with `openssl x509 -in cert.pem -noout -fingerprint -sha256`.
