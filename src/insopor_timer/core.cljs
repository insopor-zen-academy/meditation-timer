(ns insopor-timer.core
  (:require [reagent.core :as reagent :refer [atom]]
             [clojure.string :refer [replace]]))

(enable-console-print!)

(println "This text is printed from src/insopor-timer/core.cljs. Go ahead and edit it and see reloading in action.")

(defonce state (atom {:seconds 3
                      :played false}))



(defn- audio-element
  "There has to be an audio tag with id audio in the page.

  https://developer.mozilla.org/en-US/docs/Web/API/HTMLMediaElement"
  []
  (.getElementById js/document "audio"))


(defn- play-error-handler [error]
  (prn error)
  (swap! state assoc :gesture-required true))

;; http://diveintohtml5.info/everything.html
(defn can-play?
  "Checks if a given mimetype can be played by the browser."
  [mime]
  (not (empty? (and (.-canPlayType (audio-element))
                    (replace (.canPlayType (audio-element) mime) #"no" "")))))

(def ^:private formats
  [{:format "ogg" :mime "audio/ogg; codecs='vorbis'"}
   {:format "mp3" :mime "audio/mpeg;"}
   {:format "acc" :mime "audio/aac;"}
   ;;{:format "mp4" :mime "audio/mp4; codecs='mp4a.40.2'"}
   ;;{:format "m4a" :mime "audio/m4a;"}
   ;;{:format "wav" :mime "audio/wav; codecs='1'"}
   ])

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

(defn source []
  (.-currentSrc (audio-element)))

(defn source! [source]
  (let [url (supported-source source)]
    (prn "setting source to" url)
    ;;(.log js/console (streamed?) url)
    (aset (audio-element) "autoplay" "none")
    (aset (audio-element) "preload" "none")
    (aset (audio-element) "src" url)
    (aset (audio-element) "crossOrigin" "anonymous")))


(defn play! []
  (prn "playing...")
  (let [promise (.play (audio-element))]
    (.log js/console "play returned" promise)
    (if promise
      ;; if the play promise throws up^H^H an error...
      (.catch promise play-error-handler))))

(defn start-countdown []
          (js/setInterval (fn []
                            (if (> (:seconds @state) 0)
                              (swap! state update-in [:seconds] dec)
                              (if-not (:played @state)
                                (do
                                  (source! "/snip")
                                  (play!)
                                  (swap! state update-in [:played] not)))))
                                ;(js/clearInterval (:interval_pid @state)))))
                            500))

(defn timer-comp []
  [:div
   "Countdown: " (:seconds @state)])

(defn hello-world []
  [:div
   [:h1 "Meditation Timer"]
   [timer-comp]
   [:button {:on-click #(start-countdown)}
    "Start"]

   ])



;; (source! "/snip")








(reagent/render-component [hello-world]
                          (. js/document (getElementById "app")))

(defn on-js-reload []
  ;; (swap! app-state update-in [:__figwheel_counter] inc)
)
