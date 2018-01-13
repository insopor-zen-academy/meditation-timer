(ns insopor-timer.core
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs-time.core :as time]
            [cljs-time.format :as time-format]
            [clojure.string :refer [replace]]))

(enable-console-print!)

(println "This text is printed from src/insopor-timer/core.cljs. Go ahead and edit it and see reloading in action.")

(defonce interval_pids (atom []))

(defonce state (atom {:seconds 60
                      :meditating false
                      :end-time (time/now)}))


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
   {:format "acc" :mime "audio/aac;"}
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


;; --- public setter

(defn source! [source]
  (let [url (supported-source source)]
    (prn "setting source to" url)
    (aset (audio-element) "autoplay" "none")
    (aset (audio-element) "preload" "none")
    (aset (audio-element) "src" url)
    (aset (audio-element) "crossOrigin" "anonymous")))


(defn play! []
  (prn "playing...")
  (let [promise (.play (audio-element))]
    (.log js/console "play returned" promise)
    (if promise
      ;; if the play promise throws up an error
      (.catch promise play-error-handler))))

;; --- reagent helpers

(defn reset-timer []
  (swap! state assoc :meditating false)
  (loop [[pid] @interval_pids]
    (js/clearInterval pid))
  (reset! interval_pids []))

(defn start-countdown []
  (swap! state assoc :meditating true)
  (swap! interval_pids conj
         (js/setInterval (fn []
                           (if (> (:seconds @state) 0)
                             (swap! state update-in [:seconds] dec)
                             (if (:meditating @state)
                               (do
                                 (source! "/snip")
                                 (play!)
                                 (reset-timer)))))
                         500)))

(defn stop-countdown []
  (reset-timer))

;; --- reagent components

(defn timer-comp []
  [:div
   (:seconds @state) " minutes to meditate"])

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
           :disabled (if (:meditating @state)
                       true
                       false)
           :value (:seconds @state)
           :on-change (fn [e]
                        (swap! state assoc :seconds (-> e .-target .-value))
                        (swap! state assoc :end-time (time/plus (time/now)
                                                                (time/minutes
                                                                 (-> (:seconds @state)
                                                                     js/parseInt)))))}])

(defn time-comp
  "Takes a cljs-time `time` object and renders it in `hh:mm`. For
  `true`, it'll render the current time."
  [time]
  [:div
   (str
    "Ending time: "
    (time-format/unparse (time-format/formatter "hh:mm")
                         (time/to-default-time-zone (time/date-time time))))])

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
