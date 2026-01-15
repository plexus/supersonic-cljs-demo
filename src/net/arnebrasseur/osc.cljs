(ns net.arnebrasseur.osc
  "ClojureScript library for encoding and decoding OSC (Open Sound Control) messages.

  Only handles conversion between ClojureScript data and binary data, no transport.
  Supports string, int, float, double, bytes, and time tags as understood by scsynth.

  Type derivation:
  - round number -> int
  - number with decimal part -> float
  - BigInt -> double
  - string -> string
  - Uint8Array -> bytes")

(defn- detect-type [value]
  (cond
    (and (number? value) (integer? value)) :int
    (and (number? value) (not (integer? value))) :float
    (instance? js/BigInt value) :double
    (string? value) :string
    (instance? js/Uint8Array value) :bytes
    :else (throw (js/Error. (str "Unsupported type: " (type value))))))

(defn- pad-to-4 [n]
  (let [rem (mod n 4)]
    (if (zero? rem) n (+ n (- 4 rem)))))

(defn- type->tag [type]
  (case type
    :int \i
    :float \f
    :double \d
    :string \s
    :bytes \b))

(defn message-size
  "Calculate the size in bytes needed to encode an OSC message."
  [{:keys [path arguments]}]
  (let [typed-args  (mapv (fn [arg]
                            (if (vector? arg)
                              {:type (first arg) :value (second arg)}
                              {:type (detect-type arg) :value arg}))
                          arguments)
        path-len    (count path)
        path-padded (pad-to-4 (inc path-len))
        type-tags   (str "," (apply str (map (comp type->tag :type) typed-args)))
        tags-len    (count type-tags)
        tags-padded (pad-to-4 (inc tags-len))
        args-size   (reduce + (mapv (fn [arg]
                                      (case (:type arg)
                                        :int    4
                                        :float  4
                                        :double 8
                                        :string (pad-to-4 (inc (count (:value arg))))
                                        :bytes  (+ 4 (pad-to-4 (.-length (:value arg))))))
                                    typed-args))]
    (+ path-padded tags-padded args-size)))

(defonce text-encoder (js/TextEncoder.))

(defn- write-string [^js dv offset s]
  (let [dest          (js/Uint8Array. (.-buffer dv) (+ (.-byteOffset dv) offset))
        result        (.encodeInto text-encoder s dest)
        bytes-written (.-written result)
        total-bytes   (pad-to-4 (inc bytes-written))]
    (dotimes [i (- total-bytes bytes-written)]
      (.setUint8 dv (+ offset bytes-written i) 0))
    total-bytes))

(defn- read-string [dv offset]
  (let [builder (js/Array.)
        buf (js/Uint8Array. (.-buffer dv) (+ (.-byteOffset dv) offset) (- (.-byteLength dv) offset))]
    (loop [i 0]
      (if (and (< i (.-length buf)) (not (zero? (aget buf i))))
        (do (.push builder (aget buf i))
            (recur (inc i)))
        (let [str (js/String.fromCharCode.apply nil builder)]
          [str (pad-to-4 (inc i))])))))

(defn- write-int32 [dv offset value]
  (.setInt32 dv offset value false) ; false = big-endian
  4)

(defn- read-int32 [dv offset]
  (.getInt32 dv offset false))

(defn- write-float32 [dv offset value]
  (.setFloat32 dv offset value false)
  4)

(defn- read-float32 [dv offset]
  (.getFloat32 dv offset false))

(defn- write-float64 [dv offset value]
  (.setFloat64 dv offset value false)
  8)

(defn- read-float64 [dv offset]
  (.getFloat64 dv offset false))

(defn- write-blob [^js dv offset ^js bytes]
  (let [len          (.-length bytes)
        padded-len   (pad-to-4 len)
        data-offset  (+ offset 4)
        dest         (js/Uint8Array. (.-buffer dv) data-offset len)]
    (write-int32 dv offset len)
    (.set dest bytes)
    (let [padding (- padded-len len)]
      (dotimes [i padding]
        (.setUint8 dv (+ data-offset len i) 0)))
    (+ 4 padded-len)))

(defn- read-blob [dv offset]
  (let [len (read-int32 dv offset)
        padded-len (pad-to-4 len)
        bytes (js/Uint8Array. len)]
    (.set bytes (js/Uint8Array. (.-buffer dv) (+ offset 4) len))
    [bytes (+ 4 padded-len)]))

(defn- tag->type [tag]
  (case tag
    \i :int
    \f :float
    \d :double
    \s :string
    \b :bytes))

;; OSC Message encoding
(defn encode-message
  "Encode an OSC message into a DataView.

   Takes a DataView and a message map with :path and :arguments keys.
   Arguments can be values (auto-typed) or [type value] pairs for explicit typing.

   Returns the number of bytes written.

   Example:
   (encode-message buf {:path \"/test\" :arguments [123 1.5 \"hello\"]})"
  [dv {:keys [path arguments]}]
  (let [typed-args (mapv (fn [arg]
                           (if (vector? arg)
                             {:type (first arg) :value (second arg)}
                             {:type (detect-type arg) :value arg}))
                         arguments)
        type-tags (str "," (apply str (map (comp type->tag :type) typed-args)))
        path-len (count path)
        path-padded-len (pad-to-4 (inc path-len))
        tags-len (count type-tags)
        tags-padded-len (pad-to-4 (inc tags-len))]

    (let [written-path (write-string dv 0 path)
          written-tags (write-string dv written-path type-tags)
          args-offset (+ written-path written-tags)]
      (loop [offset args-offset
             args typed-args]
        (if (empty? args)
          offset
          (let [arg (first args)
                written (case (:type arg)
                          :int (write-int32 dv offset (:value arg))
                          :float (write-float32 dv offset (:value arg))
                          :double (write-float64 dv offset (:value arg))
                          :string (write-string dv offset (:value arg))
                          :bytes (write-blob dv offset (:value arg)))
                offset' (+ offset written)]
            (recur offset' (rest args))))))))

;; OSC Message decoding
(defn decode-message
  "Decode an OSC message from a DataView.

   Returns a map with :path and :arguments keys.

   Example:
   (decode-message dv) => {:path \"/test\" :arguments [123 1.5 \"hello\"]}"
  [dv]
  (let [[path path-len] (read-string dv 0)
        offset' path-len
        [type-tags tags-len] (read-string dv offset')
        offset'' (+ offset' tags-len)]
    (if (not= (first type-tags) \,)
      (throw (js/Error. "Invalid OSC message: type tags must start with comma")))
    (let [tags (rest type-tags)
          args (loop [arg-offset offset''
                      remaining-tags tags
                      results []]
                 (if (empty? remaining-tags)
                   results
                   (let [tag (first remaining-tags)
                         type (tag->type tag)
                         [value len] (case type
                                       :int [(read-int32 dv arg-offset) 4]
                                       :float [(read-float32 dv arg-offset) 4]
                                       :double [(read-float64 dv arg-offset) 8]
                                       :string (read-string dv arg-offset)
                                       :bytes (read-blob dv arg-offset))]
                     (recur (+ arg-offset len)
                            (rest remaining-tags)
                            (conj results value)))))]
      {:path path :arguments args})))

(def ^:private unix-epoch-offset 2208988800)
(def ^:private ntp-scale (Math/pow 2 32))

(defn encode-bundle
  [dv {:keys [time messages]}]
  (let [bundle-prefix "#bundle"
        written-prefix (write-string dv 0 bundle-prefix)]

    ;; Handle OSC Timetag (64-bit NTP)
    (if (or (nil? time) (zero? time) (= time 1))
      ;; Immediate
      (do
        (write-int32 dv written-prefix 0)
        (write-int32 dv (+ written-prefix 4) 1))
      ;; ms-since-epoch to NTP time tag
      (let [;; 1. Convert ms to seconds
            unix-total-seconds (/ time 1000.0)
            ;; 2. Shift to NTP epoch (1900)
            ntp-total-seconds (+ unix-total-seconds unix-epoch-offset)
            seconds (js/Math.floor ntp-total-seconds)
            ;; 3. Fractional part: (total - floor) * 2^32
            fraction (js/Math.floor (* (- ntp-total-seconds seconds) ntp-scale))]
        (write-int32 dv written-prefix seconds)
        (write-int32 dv (+ written-prefix 4) fraction)))

    (loop [offset (+ written-prefix 8)
           msgs messages]
      (if (empty? msgs)
        offset
        (let [msg (first msgs)
              msg-size (message-size msg)
              _ (write-int32 dv offset msg-size)
              ;; Calculate absolute offset for sub-view
              abs-offset (+ (.-byteOffset dv) offset 4)
              msg-dv (js/DataView. (.-buffer dv) abs-offset msg-size)]
          (encode-message msg-dv msg)
          (recur (+ offset 4 msg-size) (rest msgs)))))))

(defn decode-bundle
  "Decode an OSC bundle from a DataView.
   Returns a map with :time (Unix ms) and :messages (vector of maps)."
  [^js dv]
  (let [[prefix prefix-len] (read-string dv 0)]
    (if (not= prefix "#bundle")
      (throw (js/Error. (str "Invalid OSC bundle: expected #bundle, got " prefix))))

    (let [timetag-offset   prefix-len
          ;; 1. Read the 64-bit NTP Timetag
          seconds          (read-int32 dv timetag-offset)
          fraction         (read-int32 dv (+ timetag-offset 4))
          ;; 2. Convert NTP to Unix Milliseconds
          ;; Special case: [0, 1] means 'Immediate'
          time-val (if (and (zero? seconds) (= fraction 1))
                     1
                     (let [ntp-total-secs (+ seconds (/ fraction ntp-scale))
                           unix-ms        (* (- ntp-total-secs unix-epoch-offset) 1000)]
                       (js/Math.round unix-ms)))

          ;; 3. Prepare to loop through bundle elements
          ;; A bundle element is: [int32 size] [message or bundle data]
          first-msg-offset (+ timetag-offset 8)
          total-len        (.-byteLength dv)

          messages (loop [bundle-offset first-msg-offset
                          msgs          []]
                     (if (>= bundle-offset total-len)
                       msgs
                       (let [msg-size   (read-int32 dv bundle-offset)
                             data-start (+ bundle-offset 4)

                             ;; Create a constrained view for the individual message.
                             ;; We must add the parent DataView's byteOffset to handle nested bundles.
                             abs-offset (+ (.-byteOffset dv) data-start)
                             msg-dv     (js/DataView. (.-buffer dv) abs-offset msg-size)

                             ;; Check for nested bundles or standard messages
                             ;; OSC strings are always null-terminated.
                             is-bundle? (= (read-int32 msg-dv 0) 0x2362756e) ;; "#bun" in hex
                             msg        (if is-bundle?
                                          (decode-bundle msg-dv)
                                          (decode-message msg-dv))]

                         (recur (+ data-start msg-size)
                                (conj msgs msg)))))]
      {:time time-val :messages messages})))
