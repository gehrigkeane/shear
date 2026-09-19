# Security

## Scope

Shear handles text that users share and, only when redirect resolution is enabled, makes outbound HTTP requests.
In scope: anything that sends history or shared text off the device, bypasses the redirect network-safety limits
(hop count, scheme restrictions, private-address blocking, credential stripping), or silently corrupts a URL.

## Reporting

Use [GitHub private vulnerability reporting](https://github.com/gehrigkeane/shear/security/advisories/new).
Public issues are disabled on this repository.

## Expectations

Shear is a personal project with a single maintainer.
Reports receive a best-effort response with no SLA and no bounty.
Fixes ship as a GitHub Release.

## Verifying releases

The SHA-256 fingerprint of the release signing certificate will be published here with the first signed release.
