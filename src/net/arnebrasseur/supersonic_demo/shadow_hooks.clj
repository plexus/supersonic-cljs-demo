(ns net.arnebrasseur.supersonic-demo.shadow-hooks
  (:refer-clojure :exclude [flush])
  (:require
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]))

(defn configure
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

(defn flush
  {:shadow.build/stage :flush}
  [build-state & args]
  (shell/sh "cp" "public/index.html" "target/index.html")
  build-state)
