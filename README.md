# Shear

> Share, minus the fleece.

Shear is an Android share target that sits between the app you are sharing from and the app you are sharing to.
It strips tracking and affiliate clutter from every link in the shared text, unwraps known redirectors,
and hands the cleaned text straight back to Android's own Sharesheet.
The destination never changes; only the noise goes.

## Status

Pre-release.
There is nothing to install yet.

Shear is a personal project that exists to remove one person's inconvenience.
It is open source so the code can be inspected and reused, not because a community is being built around it.
There is no roadmap and no support commitment.
Read [CONTRIBUTING.md](./CONTRIBUTING.md) before opening a pull request.

## Why

Links copied out of apps carry `utm_*` parameters, click identifiers, affiliate tags,
and opaque redirect wrappers that follow the recipient around.
Cleaning them by hand is tedious, so most people do not.
Doing it at the moment of sharing, with no new picker to learn, makes the clean link the default one.

Shear applies [Brave's Copy Clean Link rules](https://github.com/brave/brave-browser/wiki/Copy-clean-link)
and [debouncing rules](https://github.com/brave/brave-browser/wiki/Debouncing) and nothing else.
It never invents heuristics: when no rule matches, the URL passes through byte for byte.

## Installation

Releases will ship as signed APKs on [GitHub Releases](https://github.com/gehrigkeane/shear/releases).
Install one by opening the APK on the device,
then point [Obtainium](https://github.com/ImranR98/Obtainium) at this repository to receive updates.

## Privacy

Everything stays on the device.
Share history is stored locally with a configurable retention and can be cleared at any time.
The only network traffic Shear produces is redirect resolution, which is off by default and sends no cookies,
credentials, or referrer when enabled.

## Development

See [AGENTS.md](./AGENTS.md).

## License

[MPL-2.0](./LICENSE).
The bundled Brave datasets are also MPL-2.0, from [brave/adblock-lists](https://github.com/brave/adblock-lists).
