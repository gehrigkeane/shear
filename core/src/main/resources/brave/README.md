# Brave datasets

Unmodified copies of `clean-urls.json`, `debounce.json`,
and `query-filter.json` from [brave/adblock-lists](https://github.com/brave/adblock-lists),
plus `conditional-trackers.json`, the four parameter-to-regex pairs extracted from
`components/query_filter/browser/utils.cc` in [brave/brave-core](https://github.com/brave/brave-core).
All are MPL-2.0.
`UPSTREAM` records the commits they were taken from.
Refresh them with `mise run rules-update`; never edit them by hand.
