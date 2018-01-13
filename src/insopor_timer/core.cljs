(ns insopor-timer.core
  (:require [reagent.core :as reagent :refer [atom]]
             [clojure.string :refer [replace]]))

(enable-console-print!)

(println "This text is printed from src/insopor-timer/core.cljs. Go ahead and edit it and see reloading in action.")

(defonce state (atom {:seconds 3
                      :meditating false
                      :played false}))


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

(defn reset-timer []
  (swap! state update-in [:meditating] not)
  (swap! state assoc :seconds 3)
  (js/clearInterval (:interval_pid @state)))

(defn start-countdown []
  (reset-timer)
  (swap! state assoc :interval_pid
         (js/setInterval (fn []
                           (if (> (:seconds @state) 0)
                             (swap! state update-in [:seconds] dec)
                             (if-not (:played @state)
                               (do
                                 (source! "/snip")
                                 (play!)
                                 (swap! state update-in [:played] not)))))
                         500)))

(defn stop-countdown []
  (reset-timer))

(defn timer-comp []
  [:div
   "Countdown: " (:seconds @state)])

(defn action-button []
  (cond
    (not (:meditating @state)) [:button {:on-click #(start-countdown)}
                                "Start"]
    (:meditating @state) [:button {:on-click #(stop-countdown)}
                          "Stop"]))

(defn insopor-timer []
  [:div
   [:h1 "Meditation Timer"]
   [timer-comp]
   [action-button]
   ])

(reagent/render-component [insopor-timer]
                          (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
