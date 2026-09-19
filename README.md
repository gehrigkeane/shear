# Shear

> Share, minus the fleece.

Shear is an Android share target that sits between the app you are sharing from and the app you are sharing to.
It strips tracking and affiliate clutter from every link in the shared text, unwraps known redirectors,
and hands the cleaned text straight back to Android's own Sharesheet.
The destination never changes; only the noise goes.

## Status

Pre-release.
The app works end to end, but there are no signed releases yet.

Shear is a personal project that exists to remove one person's inconvenience.
It is open source so the code can be inspected and reused, not because a community is being built around it.
There is no roadmap and no support commitment.
Read [CONTRIBUTING.md](./CONTRIBUTING.md) before opening a pull request.

## What it does

1. You pick Shear in any app's share sheet.
2. Every link in the text goes through Brave's strict clean, in Brave's order: known redirectors are unwrapped offline,
   tracking parameters from Brave's query filter are removed, then the per-site clean-URL rules run.
   The pipeline repeats until the link stops changing, so cleaning an already clean link changes nothing.
3. If you have turned on redirect resolution, opaque short links are followed over the network, one hop at a time,
   with every hop cleaned before it is fetched.
4. Android's Sharesheet opens with the cleaned text, and whichever app you choose is noted in Shear's history.

Anything that is not a link is untouched, byte for byte.
A link that Shear cannot clean is passed through exactly as you shared it.

## Why

Links copied out of apps carry `utm_*` parameters, click identifiers, affiliate tags,
and opaque redirect wrappers that follow the recipient around.
Cleaning them by hand is tedious, so most people do not.
Doing it at the moment of sharing, with no new picker to learn, makes the clean link the default one.

Shear applies [Brave's Copy Clean Link rules](https://github.com/brave/brave-browser/wiki/Copy-clean-link),
[query filter](https://github.com/brave/brave-browser/wiki/Query-String-Filter),
and [debouncing rules](https://github.com/brave/brave-browser/wiki/Debouncing) and nothing else.
It never invents heuristics: when no rule matches, the URL passes through byte for byte.

## Installation

Releases will ship as signed APKs on [GitHub Releases](https://github.com/gehrigkeane/shear/releases).
Install one by opening the APK on the device,
then point [Obtainium](https://github.com/ImranR98/Obtainium) at this repository to receive updates.
Until then, build it yourself: `mise install && mise run sdk && mise run install`.

## Privacy

Everything stays on the device.
Share history is stored locally, kept for 30 days by default, and can be set to indefinite or off, cleared at any time,
or stored without the original links.
Nothing is backed up or transferred off the device.
The only network traffic Shear produces is redirect resolution, which is off by default; when enabled,
Shear contacts the redirect service, which learns that you visited the link, and sends no cookies, credentials,
or referrer while doing so.
Links shared as plain `http://` are fetched over plain http, since the resolver never rewrites the scheme you shared.
Redirect chains can never reach the device or its local network.

## Development

See [AGENTS.md](./AGENTS.md).

## License

[MPL-2.0](./LICENSE).
The bundled Brave datasets are also MPL-2.0,
from [brave/adblock-lists](https://github.com/brave/adblock-lists)
and [brave/brave-core](https://github.com/brave/brave-core),
as is Mozilla's [Public Suffix List](https://publicsuffix.org/).
