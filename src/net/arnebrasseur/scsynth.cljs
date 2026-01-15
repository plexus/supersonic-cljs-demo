(ns net.arnebrasseur.scsynth
  (:require
   ["/gen/supersonic" :refer [SuperSonic]]
   [net.arnebrasseur.osc :as osc]
   [kitchen-async.promise :as p]))

(defonce buffer (js/DataView. (js/ArrayBuffer. (Math/pow 2 16)))) ;; 64kB is probably enough?
(defonce node-id (atom 1001))

(defonce ^js supersonic (SuperSonic. #js {:baseURL "./supersonic/"}))

(defn init! [synthdefs]
  (p/do
    (.init supersonic)
    (println "Supersonic initialized!")
    (.loadSynthDefs supersonic synthdefs)
    (println "Synthdefs loaded!")))

(defn encode-osc [msg]
  (let [byte-length (osc/encode-message buffer msg)]
    (js/Uint8Array. (.-buffer buffer) 0 byte-length)))

(defn send! [msg]
  (.sendOSC supersonic (encode-osc msg)))

(defn new-synth* [synth-name {:keys [id action target params]
                              :or {id -1 action 0 target 0}}]
  {:path "/s_new"
   :arguments (into [synth-name id action target]
                    cat
                    (for [[k v] params]
                      [(name k) v]))})


(defn free-node* [id]
  {:path "/n_free" :arguments [id]})

(defn set-node* [id params]
  {:path "/n_set"
   :arguments
   (into [id]
         cat
         (for [[k v] params]
           [(name k) v]))})

(def new-synth (comp send! new-synth*))
(def free-node (comp send! free-node*))
(def set-node (comp send! set-node*))
