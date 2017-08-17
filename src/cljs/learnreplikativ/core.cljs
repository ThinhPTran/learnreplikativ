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

(defn loginform []
  (let [input-text (:input-text @mydb/local-login)
        name (:user/name @mydb/local-login)]
    [:div.col-sm-2
     [:input
      {:id "my-input-box"
       :type "text"
       :value input-text
       :onChange (fn [_]
                   (let [v (.-value (gdom/getElement "my-input-box"))]
                     (.log js/console "change something!!!: " v)
                     (swap! mydb/local-login assoc :input-text v)))}]
     [:button#btn-login
      {:type "button"
       :onClick (fn []
                  (.log js/console "logging in!!!")
                  (events/user-login input-text))}
      "Secure login!"]
     [:div (str "input text: " input-text)]
     [:div (str "user name: " name)]]))

(defn usernames []
  (let [names (:user/names @mydb/global-users)]
    [:div.col-sm-2
     [:div "User names: "]
     [:input
      {:type "button"
       :value "Get usernames"
       :on-click (fn [_]
                   (events/get-usernames))}]
     [:ul
      (for [name names]
        ^{:key name} [:li name])]]))

(defn mylocaltable []
  (let [tableconfig (:tableconfig @mydb/local-states)
        table (atom {:table nil})]
    [:div.col-sm-4
     [:div
      {:style {:min-width "310px" :max-width "800px" :margin "0 auto"}
       :ref (fn [mydiv]
              (if (some? mydiv)
                (swap! table assoc :table
                       (js/Handsontable mydiv (clj->js (assoc-in tableconfig [:afterChange] #(do
                                                                                               (events/set-action (js->clj %)))))))
                (let [mytable (:table @table)]
                  (if (some? mytable)
                    (do
                      (.destroy mytable)
                      (swap! table assoc :table nil))))))}]]))

(defn mylocalchart []
  (let [tableconfig (:tableconfig @mydb/local-states)
        my-chart-config (utils/gen-chart-config-handson tableconfig)
        chart (atom {:chart nil})]
    [:div.col-sm-4
     [:div
      {:style {:height "100%" :width "100%" :position "relative"}
       :ref (fn [mydiv]
              (if (some? mydiv)
                (swap! chart assoc :chart (js/Highcharts.Chart. mydiv (clj->js @my-chart-config)))
                (let [mychart (:chart @chart)]
                  (if (some? mychart)
                    (do
                      (.destroy mychart)
                      (swap! chart :chart nil))))))}]]))

(defn mylocaltransacts []
  (let [listactions (:listactions @mydb/local-states)]
    [:div.col-sm-4
     [:h2 "Local actions: "]
     [:ul
      (for [action listactions]
        ^{:key action} [:li (str (:user action) " changed at " (:inst action))])]]))

(defn myglobaltable []
  (let [tableconfig (:tableconfig @mydb/global-states)
        table (atom {:table nil})]
    [:div.col-sm-4
     [:div
      {:style {:min-width "310px" :max-width "800px" :margin "0 auto"}
       :ref (fn [mydiv]
              (if (some? mydiv)
                (swap! table assoc :table
                       (js/Handsontable mydiv (clj->js (assoc-in tableconfig [:afterChange] #(do
                                                                                               (events/set-action (js->clj %)))))))
                (let [mytable (:table @table)]
                  (if (some? mytable)
                    (do
                      (.destroy mytable)
                      (swap! table assoc :table nil))))))}]]))

(defn myglobalchart []
  (let [tableconfig (:tableconfig @mydb/global-states)
        my-chart-config (utils/gen-chart-config-handson tableconfig)
        chart (atom {:chart nil})]
    [:div.col-sm-4
     [:div
      {:style {:height "100%" :width "100%" :position "relative"}
       :ref (fn [mydiv]
              (if (some? mydiv)
                (swap! chart assoc :chart (js/Highcharts.Chart. mydiv (clj->js @my-chart-config)))
                (let [mychart (:chart @chart)]
                  (if (some? mychart)
                    (do
                      (.destroy mychart)
                      (swap! chart :chart nil))))))}]]))

(defn myglobaltransacts []
  (let [listactions (:listactions @mydb/global-states)]
    [:div.col-sm-4
     [:h2 "Local actions: "]
     [:ul
      (for [action listactions]
        ^{:key action} [:li (str (:user action) " changed at " (:inst action))])]]))

(defn myslider []
  (let [totalactions (:totalactions @mydb/global-states)
        currentpick (:currentpick @mydb/global-states)
        totallistactions (:totallistactions @mydb/global-states)
        username (if (= currentpick 0) "No Information" (:user (get totallistactions (- currentpick 1))))
        instant (if (= currentpick 0) "No Information" (:inst (get totallistactions (- currentpick 1))))]
    [:div.col-sm-12
     [:input#myrange {:type "range"
                      :min 0
                      :max totalactions
                      :value currentpick
                      :step 1
                      :onChange (fn [_]
                                  (let
                                    [v (.-value (gdom/getElement "myrange"))]
                                    ;(.log js/console "value: " v)
                                    (events/set-history-point (js/parseInt v))))}]
     [:div (str "Username: " username)]
     [:div (str "at: " instant)]]))

(defn home-page []
  [:div
   [:h2 "Welcome to my Datascript experiment"]
   [:div.row
    [loginform]
    [usernames]]
   [:div.row
    [mylocaltable]
    [mylocalchart]
    [mylocaltransacts]]
   [:div.row
    [myslider]]
   [:div.row
    [myglobaltable]
    [myglobalchart]
    [myglobaltransacts]]])

(defn mount-root []
  (r/render [home-page] (.getElementById js/document "app")))

(defn on-js-reload []
  (mount-root))

(defn ^:export main [& args]
  (dev-setup)
  (mydb/setupclientdata)
  (mount-root))