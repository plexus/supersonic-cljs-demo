# SuperSonic ClojureScript Demo

[**Try it out!**](https://arnebrasseur.net/supersonic-cljs/)

[SuperSonic](https://github.com/samaaron/supersonic) is a project by Sam Aaron
(of Sonic Pi and Overtone fame), to compile Supercollider (the sound synthesis
engine underlying both Overtone and Sonic Pi) to WASM, so it can be used in a
browser.

This project demonstrates how to use SuperSonic from ClojureScript.

## How this works

You can load SuperSonic assets from CDN:

```clj
(defonce ^js supersonic
  (SuperSonic. #js {:baseURL "https://unpkg.com/supersonic-scsynth@latest/dist/"
                    :synthdefBaseURL "https://unpkg.com/supersonic-scsynth-synthdefs@latest/synthdefs/"}))
```

Or serve them locally (see shadow-cljs build hooks for the setup).

See the `scsynth` and `demo` namespaces for usage.

## Dev / build

For a dev environment, see [Launchpad](https://github.com/lambdaisland/launchpad)

```
pnpm i
bin/launchpad
```

For a prod build

```
pnpm i
npx shadow-cljs release supersonic-demo
```
