(ns net.arnebrasseur.supersonic-demo
  (:require
   [clojure.string :as str]
   [net.arnebrasseur.scsynth :as sc]))

(def synthdefs
  #js [
       ;; "sonic-pi-amp_stereo_monitor"
       ;; "sonic-pi-basic_mixer"
       ;; "sonic-pi-basic_mono_player"
       ;; "sonic-pi-basic_stereo_player"
       "sonic-pi-bass_foundation"
       "sonic-pi-bass_highend"
       "sonic-pi-beep"
       "sonic-pi-blade"
       "sonic-pi-bnoise"
       "sonic-pi-chipbass"
       "sonic-pi-chiplead"
       "sonic-pi-chipnoise"
       "sonic-pi-cnoise"
       "sonic-pi-dark_ambience"
       "sonic-pi-dpulse"
       "sonic-pi-dsaw"
       "sonic-pi-dtri"
       "sonic-pi-dull_bell"
       "sonic-pi-fm"
       ;; "sonic-pi-fx_autotuner"
       ;; "sonic-pi-fx_band_eq"
       ;; "sonic-pi-fx_bitcrusher"
       ;; "sonic-pi-fx_bpf"
       ;; "sonic-pi-fx_compressor"
       ;; "sonic-pi-fx_distortion"
       ;; "sonic-pi-fx_echo"
       ;; "sonic-pi-fx_eq"
       ;; "sonic-pi-fx_flanger"
       ;; "sonic-pi-fx_gverb"
       ;; "sonic-pi-fx_hpf"
       ;; "sonic-pi-fx_ixi_techno"
       ;; "sonic-pi-fx_krush"
       ;; "sonic-pi-fx_level"
       ;; "sonic-pi-fx_lpf"
       ;; "sonic-pi-fx_mono"
       ;; "sonic-pi-fx_nbpf"
       ;; "sonic-pi-fx_nhpf"
       ;; "sonic-pi-fx_nlpf"
       ;; "sonic-pi-fx_normaliser"
       ;; "sonic-pi-fx_nrbpf"
       ;; "sonic-pi-fx_nrhpf"
       ;; "sonic-pi-fx_nrlpf"
       ;; "sonic-pi-fx_octaver"
       ;; "sonic-pi-fx_pan"
       ;; "sonic-pi-fx_panslicer"
       ;; "sonic-pi-fx_ping_pong"
       ;; "sonic-pi-fx_pitch_shift"
       ;; "sonic-pi-fx_rbpf"
       ;; "sonic-pi-fx_record"
       ;; "sonic-pi-fx_reverb"
       ;; "sonic-pi-fx_rhpf"
       ;; "sonic-pi-fx_ring_mod"
       ;; "sonic-pi-fx_rlpf"
       ;; "sonic-pi-fx_scope_out"
       ;; "sonic-pi-fx_slicer"
       ;; "sonic-pi-fx_sound_out"
       ;; "sonic-pi-fx_sound_out_stereo"
       ;; "sonic-pi-fx_tanh"
       ;; "sonic-pi-fx_tremolo"
       ;; "sonic-pi-fx_vowel"
       ;; "sonic-pi-fx_whammy"
       ;; "sonic-pi-fx_wobble"
       "sonic-pi-gabberkick"
       "sonic-pi-gnoise"
       "sonic-pi-growl"
       "sonic-pi-hollow"
       "sonic-pi-hoover"
       "sonic-pi-kalimba"
       ;; "sonic-pi-live_audio"
       ;; "sonic-pi-live_audio_mono"
       ;; "sonic-pi-live_audio_stereo"
       "sonic-pi-mixer"
       ;; "sonic-pi-mod_dsaw"
       ;; "sonic-pi-mod_fm"
       ;; "sonic-pi-mod_pulse"
       ;; "sonic-pi-mod_saw"
       ;; "sonic-pi-mod_sine"
       ;; "sonic-pi-mod_tri"
       ;; "sonic-pi-mono_player"
       "sonic-pi-noise"
       "sonic-pi-organ_tonewheel"
       "sonic-pi-piano"
       "sonic-pi-pluck"
       "sonic-pi-pnoise"
       "sonic-pi-pretty_bell"
       "sonic-pi-prophet"
       "sonic-pi-pulse"
       ;; "sonic-pi-recorder"
       "sonic-pi-rhodey"
       "sonic-pi-rodeo"
       "sonic-pi-saw"
       "sonic-pi-sc808_bassdrum"
       "sonic-pi-sc808_clap"
       "sonic-pi-sc808_claves"
       "sonic-pi-sc808_closed_hihat"
       "sonic-pi-sc808_congahi"
       "sonic-pi-sc808_congalo"
       "sonic-pi-sc808_congamid"
       "sonic-pi-sc808_cowbell"
       "sonic-pi-sc808_cymbal"
       "sonic-pi-sc808_maracas"
       "sonic-pi-sc808_open_hihat"
       "sonic-pi-sc808_rimshot"
       "sonic-pi-sc808_snare"
       "sonic-pi-sc808_tomhi"
       "sonic-pi-sc808_tomlo"
       "sonic-pi-sc808_tommid"
       ;; "sonic-pi-server-info"
       ;; "sonic-pi-sound_in"
       ;; "sonic-pi-sound_in_stereo"
       "sonic-pi-square"
       ;; "sonic-pi-stereo_player"
       "sonic-pi-subpulse"
       "sonic-pi-supersaw"
       "sonic-pi-tb303"
       "sonic-pi-tech_saws"
       "sonic-pi-tri"
       "sonic-pi-winwood_lead"
       "sonic-pi-zawa"
       ;; "sonic-pi-scope"
       ])

(defn synth [name]
  (fn [& {:as params}]
    (let [id (swap! sc/node-id inc)]
      (sc/new-synth name {:id id :params params})
      id)))

(defn html [& s]
  (let [tmpl (js/document.createElement "template")]
    (set! (.-innerHTML tmpl) (apply str s))
    (.. tmpl -content -firstChild)))

(defn hexapoints [r]
  (str/join " "
            (for [i (range 6)]
              (str (.toFixed (* r (Math/cos (* (/ Math/PI 3) i))) 2) ","
                   (.toFixed (* r (Math/sin (* (/ Math/PI 3) i))) 2)))))

(defn hexapos [r x y]
  [(* 1.5 r x)
   (* (Math/sqrt 3) r (+ y (if (even? x) 0.5 0)))])

(defn hex->note [^js el]
  (let [hx (parse-long (.getAttribute el "data-hx"))
        hy (parse-long (.getAttribute el "data-hy"))]
    ;; up and to the right = up a third
    ;; down and to the right = up a fifth
    (+ 60
       (* hx 4)
       (* (+ hy (Math/floor (* 0.5 hx))) 3))))

(defn hex-grid-html []
  (let [w js/window.innerWidth
        h js/window.innerHeight
        half-w (* w 0.5)
        half-h (* h 0.5)
        r 50]
    (html "<svg viewBox='" (- half-w) " " (- half-h) " " w " " h "'>"
          "<defs>"
          "<polygon id='hex' points='" (hexapoints r) "' fill='#ff9900' stroke=white stroke-width=2 />"
          "</defs>"
          (apply
           str
           (for [hx (range -20 20)
                 hy (range -10 10)]
             (let [[x y] (hexapos r hx hy)]
               (str "<use href='#hex' data-hx=" hx " data-hy=" hy " x=" x " y=" y " />"))))
          "</svg>")))

(def pointers {})
(def current-synth nil)

(defn play! [e note]
  (let [pid (.-pointerId e)]
    (when-let [node-id (second (get pointers pid))]
      (sc/free-node node-id))
    (set! pointers (assoc pointers pid [note (current-synth :note note)]))))

(defn main! []
  (js/document.body.appendChild (hex-grid-html))

  (let [select  (html "<select>"
                      (for [s synthdefs]
                        (str "<option" (when (= "sonic-pi-prophet" s) " selected") " value='" s "'>"s "</option>"))
                      "</select>")]
    (js/document.body.appendChild select)
    (.addEventListener select "change" (fn [e] (set! current-synth (synth (.-value (.-target e)))))))

  (.addEventListener (js/document.querySelector "#start-button")
                     "click"
                     (fn [e]
                       (.setAttribute (.-target e) "disabled" "disabled")
                       (.then (sc/init! synthdefs)
                              #(.remove (.-target e)))))
  (set! current-synth (synth "sonic-pi-prophet"))

  (doseq [el (js/document.querySelectorAll "use")]
    (.addEventListener el "pointerdown"
                       (fn [e]
                         (play! e (hex->note el)))))

  (js/window.addEventListener "pointermove"
                              (fn [e]
                                (let [el (js/document.elementFromPoint (.-clientX e) (.-clientY e))]
                                  (when (and (= "use" (.-tagName el))
                                             (get pointers (.-pointerId e)))
                                    (let [note (hex->note el)
                                          [note' node-id] (get pointers (.-pointerId e))]
                                      (when (not= note note')
                                        (play! e (hex->note el))))))))

  (js/window.addEventListener "pointerup"
                              (fn [e]
                                (let [pid (.-pointerId e)]
                                  (when-let [node-id (second (get pointers pid))]
                                    (sc/free-node node-id))
                                  (set! pointers (dissoc pointers (.-pointerId e)))))))

(.addEventListener js/document "DOMContentLoaded" main!)


(comment

  (def prophet (synth "sonic-pi-prophet"))
  (def blade (synth "sonic-pi-blade"))



  (js/document.addEventListener "click" #(sc/init! synthdefs))
  )
