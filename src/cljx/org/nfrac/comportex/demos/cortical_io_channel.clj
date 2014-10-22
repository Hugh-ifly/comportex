(ns org.nfrac.comportex.demos.cortical-io-channel
  (:require [org.nfrac.comportex.core :as core]
            [org.nfrac.comportex.protocols :as p]
            [org.nfrac.comportex.cortical-io :as cio
             :refer [cortical-io-encoder]]
            [clojure.string :as str]
            [clojure.core.async :as async
             :refer [chan <!! >!! put! thread close!]]))

(def spec
  {:column-dimensions [40 40]
   :ff-init-frac 0.30
   :ff-potential-radius 0.15
   :ff-perm-inc 0.05
   :ff-perm-dec 0.005
   :ff-perm-connected 0.20
   :ff-stimulus-threshold 3
   :global-inhibition false
   :activation-level 0.03
   :duty-cycle-period 100000
   :max-boost 2.0
   ;; sequence memory:
   :depth 8
   :max-segments 5
   :seg-max-synapse-count 18
   :seg-new-synapse-count 12
   :seg-stimulus-threshold 9
   :seg-learn-threshold 6
   :distal-perm-connected 0.20
   :distal-perm-inc 0.05
   :distal-perm-dec 0.01
   :distal-perm-init 0.16
   :distal-punish? false
   :inhibition-base-distance 2
   :inhibition-speed 0.25
   })

(def min-votes 2)

(defn split-sentences
  [text]
  (->> (str/split (str/trim text) #"[^\w]*[\.\!\?]+[^\w]*")
       (mapv #(str/split % #"[^\w']+"))
       ;(mapv #(conj % "."))
       ))

(defn sensory-input-from-channel
  "Here input-c is a channel to read words from. Transitions of the
   input use a blocking take from the channel."
  [api-key input-c cache]
  (let [encoder (cortical-io-encoder api-key cache min-votes)
        xform (fn [_] (<!! input-c))]
    (core/sensory-input nil xform encoder)))

(defn n-region-model
  "Here c is a channel to read sentences from. It must contain at
   least one item when calling this or it will block!"
  [api-key input-c cache n]
  (core/regions-in-series core/sensory-region
                          (sensory-input-from-channel api-key input-c cache)
                          n spec))

(comment

  ;; interactive session feeding in text and requesting predictions

  (def api-key blah)

  ;; allow http requests to go in parallel to model running
  (def preload-c (chan 1000))
  (def input-c (chan 5))
  (def cache (atom {}))
  ;; the HTM model
  (def current (atom (n-region-model api-key input-c cache 1)))

  (defn submit
    [txt]
    (let [words (apply concat (split-sentences txt))]
      (doseq [word words]
        (>!! preload-c word))))

  (def preloader
    (thread
      (try
        (loop []
          (when-let [term (<!! preload-c)]
            (println term)
            (cio/look-up-fingerprint api-key cache term)
            (>!! input-c term)
            (recur)))
        (catch Exception e
          (println e)))))

  (def stepper
    (thread
      (try
        (loop []
          (swap! current p/feed-forward-step)
          (recur))
        (catch Exception e
          (println e)))))

  (defn predict
    [n]
    (println (map :value (core/predictions @current n))))

  (submit "greetings earth, greetings earth, greetings earth, greetings earth")
  (submit "neural networks, neural networks, neural networks, neural networks")
  (predict 3)

  (defn stream-of-associations
    [n]
    (loop [i n]
      (when (pos? i)
       (let [p-word (:value (first (core/predictions @current 1)))]
         (when p-word
           (submit p-word)
           (Thread/sleep 1000)
           (recur (dec i)))))))

  (stream-of-associations 5)

  (close! preload-c)
  (close! input-c)
  (close! preloader)
  (close! stepper)

  )