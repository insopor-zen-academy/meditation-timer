(ns insopor-timer.core
  (:require [reagent.core :as reagent :refer [atom]]
            [goog.string :as goog-string]
            [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            [cljs-time.coerce :as time-coerce]
            [clojure.string :refer [replace]]))

(enable-console-print!)

(defonce interval_pids (atom []))

(defn- now-plus-seconds
  "Adds n `seconds` to the current time.
  Returns a cljs-time object"
  [seconds]
  (time/plus (time/now)
             (time/seconds seconds)))

(defonce state (atom {:seconds (* 60 60)
                      :sound "inkin"
                      :debug false
                      :speedup false
                      :meditating false
                      :end-time (now-plus-seconds (* 60 60))
                      :start-time (time/now)
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

(defn- scheduled-time []
  (if (:speedup @state)
    (/ (:seconds @state) 60)
    (:seconds @state)))

(defn- seconds-to-end
  "Calculates the remaining seconds from `now` to `:end-time`"
  []
  (/ (- (.getTime (time-coerce/to-date
                   (:end-time @state)))
        (.getTime (js/Date.)))
     1000))

;; --- public setter


(defn schedule-play! []
  (prn "scheduling play...")
  (let [audio_context (or js/AudioContext
                          js/webkitAudioContext)
        context (audio_context.)
        url (supported-source (str "./sounds/" (:sound @state)))]

    (load-sound url context (fn [buffer]
                              (let [source (.createBufferSource context)]
                                (set! (.-buffer source) buffer)
                                (.connect source (.-destination context))
                                (swap! state assoc :audio-source source)
                                (.start source (+ (scheduled-time)
                                                  (.-currentTime context))))))))

(defn- set-start-time! []
  (swap! state assoc :start-time (time/now)))

(defn- set-end-time! []
  (swap! state assoc :end-time (now-plus-seconds (:seconds @state))))

;; --- reagent helpers

(defn reset-timer []
  (swap! state assoc :meditating false)
  (swap! state assoc :seconds 0)
  (loop [[pid] @interval_pids]
    (js/clearInterval pid))
  (reset! interval_pids []))

(defn start-countdown []
  (swap! state assoc :meditating true)
  (set-start-time!)
  (set-end-time!)
  (schedule-play!)
  (swap! interval_pids conj
         (js/setInterval (fn []
                           (if (> (:seconds @state) 0)
                             (swap! state assoc :seconds (seconds-to-end))
                             (if (:meditating @state)
                               (do
                                 (reset-timer)))))
                         1000)))

(defn stop-countdown []
  (.stop (:audio-source @state))
  (reset-timer))

;; --- reagent components

(defn timer-comp []
  [:div.timer
   [:strong
    (goog-string/format "%02i"
                        (int (/ (:seconds @state) 60)))
    ":"
    (goog-string/format "%02i"
                        (mod (:seconds @state) 60))]
   " (mm:ss) to meditate"])

(defn action-button-comp []
  [:div.action-buttons
   (cond
     (not (:meditating @state)) [:button {:on-click #(start-countdown)}
                                 "Start"]
     (:meditating @state) [:button {:on-click #(stop-countdown)}
                           "Stop"])])

(defn time-input-comp []
  [:input {:type "range"
           :class "slider"
           :min "0"
           :max "240"
           :disabled (:meditating @state)
           :value (/ (:seconds @state) 60)
           :on-change (fn [e]
                        (swap! state assoc :seconds (* 60 (js/parseInt (-> e .-target .-value))))
                        (set-start-time!)
                        (set-end-time!))}])

(defn time-comp
  "Takes a cljs-time `time` object and renders it in `hh:mm`. For
  `true`, it'll render the current time."
  [time]
  [:span
   (time-format/unparse (time-format/formatter "hh:mm")
                        (time/to-default-time-zone (time/date-time time)))])

(defn sound-comp []
  [:select.sound-select
   {:defaultValue (:sound @state)
    :on-change #(swap! state assoc :sound (-> % .-target .-value))}
   [:option {:value "inkin"} "Inkin"]
   [:option {:value "snip"} "Snip"]])

(defn debug-comp []
  [:div
   "Debug: "
   [:input {:type "checkbox"
            :on-change #(swap! state update-in [:debug] not)}]
   (if (:debug @state)
     [:div (str @state)])])

(defn speedup-comp []
  [:div
   "Speed: "
   [:input {:type "checkbox"
            :disabled (:meditating @state)
            :on-change #(swap! state update-in [:speedup] not)}]])

(defn debug-features-com []
  [:div.debug-features
   ;; [speedup-comp]
   [debug-comp]])

(defn insopor-timer []
  [:div.jumbotron.vertical-center
   [:div.container
    [:center
     [:h1
      [:img.logo {:src "./logo.png"}]
      "Meditation Timer"]
     [:div.timer-information
      [timer-comp]
      [:div.ending-time
       "Ending time: "
       [:strong
        [time-comp (:end-time @state)]]]
      [:div.starting-time
       "Start time: "
       [:strong
        [time-comp (:start-time @state)]]]]
     [:div.row
      [:div.col-sm-10
       [time-input-comp]]
      [:div.col-sm-2
       [sound-comp]]]
     [action-button-comp]]
    [debug-features-com]]])


;; -- reagent initialization

(reagent/render-component [insopor-timer]
                          (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
  )
