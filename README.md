# SuperSonic ClojureScript Demo

[SuperSonic](https://github.com/samaaron/supersonic) is a project by Sam Aaron
(of Sonic Pi and Overtone fame), to compile Supercollider (the sound synthesis
engine underlying both Overtone and Sonic Pi) to WASM, so it can be used in a
browser.

This project demonstrates how to use SuperSonic from ClojureScript. There are a
few challenges involved.

SuperSonic consists of a few different pieces:

- `scsynth.wasm` — a binary blob of C++ code compiled to WASM, this gets loaded
  over HTTP, so we need to somehow serve this up
- `workers/*.js` — SuperSonic makes use of AudioWorklets to get messages into
  scsynth, and audio out, these too need to be loaded over HTTP
- `supersonic.js` — wrapper handling the loading of these different pieces, and
  providing a JS API
  
If you want to load synths (synthdefs) or samples, then these need to be
available as well. SuperSonic ships with a bunch of Sonic-Pi synths, and we'll
use these for this demo. You can build and export synths using SuperCollider,
Sonic-Pi, or Overtone.

## How this works

We install the npm packages "supersonic-scsynth" (wrapper/loader script),
"supersonic-scsynth-core" (wasm blob), "supersonic-scsynth-synthdefs" (synths).
Unfortunately the wrapper script makes use of some features (`import.meta`)
which clojurescript (really the closure compiler) doesn't like, so we precompile
it with esbuild with a few well chosen `--define` flags to strip out the
offending expressions, and write this to `resources/gen/supersonic.js`.

The wasm, workers, and synths, we copy to `target` where we also output our
compile targets.

All of this happens from a shadow-cljs hook,

```clj
(ns net.arnebrasseur.supersonic-demo.shadow-hooks
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]))

(defn hook
  {:shadow.build/stage :configure}
  [build-state & args]
  (.mkdirs (io/file "resources/gen"))
  (.mkdirs (io/file "target/supersonic"))
  (println
   (:err
    (shell/sh
     "esbuild" "supersonic-scsynth"
     "--bundle"
     "--format=cjs"
     "--define:import.meta={}"
     "--define:import.meta.url=\"\""
     "--define:import.meta.env={}"
     "--define:import.meta.env.MODE=\"production\""
     "--define:import.meta.env.UMBRELLA_ASSERTS=true"
     "--define:process={}"
     "--define:buffer=null"
     "--outfile=resources/gen/supersonic.js")))
  (shell/sh "cp" "-r"
            "node_modules/supersonic-scsynth-core/wasm"
            "node_modules/supersonic-scsynth-core/workers"
            "node_modules/supersonic-scsynth-synthdefs/synthdefs"
            "target/supersonic")
  build-state)
```

Now we can load SuperSonic like any other JS library:

```clj
(ns net.arnebrasseur.scsynth
  (:require
   ["/gen/supersonic" :refer [SuperSonic]]))
```

See the scsynth and demo namespaces for where to go from there.

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
