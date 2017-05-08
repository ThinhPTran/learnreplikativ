(ns learnreplikativ.core
  (:require [goog.dom :as gdom]
            [hasch.core :refer [uuid]]
            [cljsjs.material-ui] ;; TODO why?
            [cljs-react-material-ui.core :as ui]
            [cljs-react-material-ui.icons :as ic]
            [reagent.core :as r]
            [sablono.core :as html :refer-macros [html]]
            [superv.async :refer [S] :as sasync]
            [replikativ.crdt.ormap.stage :as s]
            [learnreplikativ.utils :as utils]
            [learnreplikativ.events :as events]
            [learnreplikativ.db :as mydb])
  (:require-macros [superv.async :refer [go-try <? go-loop-try]]
                   [cljs.core.async.macros :refer [go-loop]]))

(def debug?
  ^boolean js/goog.DEBUG)

(defn dev-setup []
  (when debug?
    (enable-console-print!)
    (println "dev mode")))

(defn usernames []
  (let [names (:user/names @mydb/global-users)]
    [:div
     [:h2 "User names: "]
     [:input
      {:type "button"
       :value "Get usernames"
       :on-click (fn [_]
                   (.log js/console "thinh")
                   (swap! mydb/global-users assoc :user/names [(str "thinh" (rand-int 50))]))}]
     [:ul
      (for [name names]
        ^{:key name} [:li name])]]))

(defn home-page []
  [:div
   [:h2 "Welcome to my Datascript experiment"]
   [usernames]])

(defn mount-root []
  (r/render [home-page] (.getElementById js/document "app")))

(defn on-js-reload []
  (mount-root))

(defn ^:export main [& args]
  (dev-setup)
  (mydb/setupclientdata)
  (mount-root))