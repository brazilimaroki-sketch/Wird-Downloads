# Sectigo certificate provenance

Only public CA certificates are bundled. No private keys or credentials.

Root: Sectigo Public Server Authentication Root R46
- SHA-256: 7bb647a62aeeac88bf257aa522d01ffea395e0ab45c73f93f65654ec38f25a06
- Extracted from Mozilla CA bundle distributed by certifi: https://api.github.com/repos/certifi/python-certifi/git/blobs/9a40f439e0d77f3733b76489ae3bb42eaaf021d6
- Bundle blob SHA: 9a40f439e0d77f3733b76489ae3bb42eaaf021d6 (Git blob identifier; not a commit identifier).
- Official issuer reference: https://www.sectigo.com/knowledge-base/detail/Sectigo-new-Public-Roots-and-Issuing-CAs-Hierarchy

Intermediate: Sectigo Public Server Authentication CA DV R36
- SHA-256: 8c54c334b66ba4e426772af4a3f9136c19a1aec729fdb28c535c07a5a4ef22e0
- Official certificate identity and SHA-256 appear in Sectigo CA hierarchy: https://www.sectigo.com/uploads/resources/Sectigo-CA-Heirarchy-v4.pdf
- PEM transport mirror: https://www.tbs-certificates.co.uk/FAQ/en/SectigoPublicServerAuthenticationCADVR36.html
- The mirror bytes were checked against the official SHA-256 and signature-verified under the Mozilla/certifi R46 root with OpenSSL.

Obtained 2026-10-16. Both are restricted to the two Ministry hostnames by Android domain-config. These files are additional trust anchors, not a replacement for the system store. Leaf certificate validation and hostname validation remain with Android. Trust anchors must be reviewed with future CA changes.
