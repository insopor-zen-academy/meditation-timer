(ns insopor-timer.core
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            [clojure.string :refer [replace]]))

(enable-console-print!)

(defonce interval_pids (atom []))

(defn- now-plus-minutes
  "Adds n `minutes` to the current time.
  Returns a cljs-time object"
  [minutes]
  (time/plus (time/now)
             (time/minutes minutes)))

(defonce state (atom {:seconds 60
                      :meditating false
                      :end-time (now-plus-minutes 60)
                      :audio-source nil}))


;; --- private

(defn- audio-element
  []
  (.getElementById js/document "audio"))

(defn- play-error-handler [error]
  (prn "Error playing!")
  (prn error))

(def ^:private formats
  [{:format "ogg" :mime "audio/ogg; codecs='vorbis'"}
   {:format "mp3" :mime "audio/mpeg;"}
   {:format "m4a" :mime "audio/aac;"}
   ;;{:format "mp4" :mime "audio/mp4; codecs='mp4a.40.2'"}
   ;;{:format "m4a" :mime "audio/m4a;"}
   ;;{:format "wav" :mime "audio/wav; codecs='1'"}
   ])

;; http://diveintohtml5.info/everything.html
(defn can-play?
  "Checks if a given mimetype can be played by the browser."
  [mime]
  (not (empty? (and (.-canPlayType (audio-element))
                    (replace (.canPlayType (audio-element) mime) #"no" "")))))

(defn- can-play-reducer [result {:keys [format mime]}]
  (if (can-play? mime) (conj result format) result))

(defn supported-formats
  "Returns a vector of formats that the browser can play."
  []
  (reduce can-play-reducer [] formats))

(defn- supported-source
  "Appends the first supported format extension to the given url."
  [source]
  (str source "." (first (supported-formats))))

(defn- load-sound
  "XMLHttpRequest to load a sound sample under `url` into the Web
  Audio API `context`. Calls `callback` with a resulting `buffer`."
  [url context callback]
  (let [request (js/XMLHttpRequest.)]
    (.open request "GET" url true)
    (set! (.-responseType request) "arraybuffer")
    (set! (.-onload request) (fn []
                               (.decodeAudioData
                                context
                                (.-response request)
                                (fn [buffer]
                                  (callback buffer))
                                (fn [e]
                                  (prn "Error loading sound: " e)))))
    (.send request)))


;; --- public setter


(defn schedule-play! []
  (prn "scheduling play...")
  (let [audio_context (or js/AudioContext
                          js/webkitAudioContext)
        context (audio_context.)
        url (supported-source "./sounds/inkin")]

    (load-sound url context (fn [buffer]
                              (let [source (.createBufferSource context)]
                                (set! (.-buffer source) buffer)
                                (.connect source (.-destination context))
                                (swap! state assoc :audio-source source)
                                (.start source (+ (/ (:seconds @state)
                                                     2)
                                                  (.-currentTime context))))))))

;; --- reagent helpers

(defn reset-timer []
  (swap! state assoc :meditating false)
  (loop [[pid] @interval_pids]
    (js/clearInterval pid))
  (reset! interval_pids []))

(defn start-countdown []
  (swap! state assoc :meditating true)
  (schedule-play!)
  (swap! interval_pids conj
         (js/setInterval (fn []
                           (if (> (:seconds @state) 0)
                             (swap! state update-in [:seconds] dec)
                             (if (:meditating @state)
                               (do
                                 (reset-timer)))))
                         500)))

(defn stop-countdown []
  (.stop (:audio-source @state))
  (reset-timer))

;; --- reagent components

(defn timer-comp []
  [:div
   [:strong
    (:seconds @state)]
   " minutes to meditate"])

(defn action-button []
  (cond
    (not (:meditating @state)) [:button {:on-click #(start-countdown)}
                                "Start"]
    (:meditating @state) [:button {:on-click #(stop-countdown)}
                          "Stop"]))

(defn time-input-comp []
  [:input {:type "range"
           :min "0"
           :max "240"
           :disabled (:meditating @state)
           :value (:seconds @state)
           :on-change (fn [e]
                        (swap! state assoc :seconds (js/parseInt (-> e .-target .-value)))
                        (swap! state assoc :end-time (now-plus-minutes (:seconds @state))))}])

(defn time-comp
  "Takes a cljs-time `time` object and renders it in `hh:mm`. For
  `true`, it'll render the current time."
  [time]
  [:div
   "Ending time: "
   [:strong
    (time-format/unparse (time-format/formatter "hh:mm")
                         (time/to-default-time-zone (time/date-time time)))]])

(defn insopor-timer []
  [:div
   [:center
    [:img {:src "./logo.png"}]
    [:h1 "Meditation Timer"]
    [:div
     [timer-comp]]
    [time-comp (:end-time @state)]
    [:div
     [time-input-comp]]
    [:div
     [action-button]]]])


;; -- reagent initialization

(reagent/render-component [insopor-timer]
                          (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
