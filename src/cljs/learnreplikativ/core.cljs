(ns learnreplikativ.core
  (:require [hasch.core :refer [uuid]]
            [cljsjs.material-ui] ;; TODO why?
            [om.next :as om :refer-macros [defui] :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs-react-material-ui.core :as ui]
            [cljs-react-material-ui.icons :as ic]
            [sablono.core :as html :refer-macros [html]]
            [cljs-react-material-ui.core :as ui]
            [cljs-react-material-ui.icons :as ic]
            [superv.async :refer [S] :as sasync]
            [replikativ.crdt.ormap.stage :as s]
            [learnreplikativ.db :as mydb])
  (:require-macros [superv.async :refer [go-try <? go-loop-try]]
                   [cljs.core.async.macros :refer [go-loop]]))

;; Setup on client to communicate.
(declare client-state)
;; this is the only state changing function
(defn send-message! [app-state msg]
  (s/assoc! (:stage client-state)
            [mydb/user mydb/ormap-id]
            (uuid msg)
            [['assoc msg]]))
;; Don't touch to the part above.


;; helper functions
(defn format-time [d]
  (let [secs (-> (.getTime (js/Date.))
                 (- d)
                 (/ 1000)
                 js/Math.floor)]
    (cond
      (>= secs 3600) (str (js/Math.floor (/ secs 3600)) " hours ago")
      (>= secs 60) (str (js/Math.floor (/ secs 60)) " minutes ago")
      (>= secs 0) (str  " seconds ago"))))

;; Material UI with Om
(defn create-msg [name text]
  {:text text
   :name name
   :date (.getTime (js/Date.))})

(defn target-val [e]
  (.. e -target -value))

(defn name-field [comp input-name]
  (dom/div #js {:className "center-xs"}
           (ui/text-field
             {:floating-label-text "Name"
              :class-name "w-80"
              :on-change #(om/update-state! comp assoc :input-name (target-val %))
              :value input-name})))

(defn message-field [comp input-text input-name]
  (let [app-state (om/props comp)]
    (dom/div #js {:className "center-xs" :key "message"}
             (ui/text-field {:floating-label-text "Message"
                             :class-name "w-80"
                             :on-change
                                                  #(om/update-state!
                                                     comp assoc :input-text (target-val %))
                             :on-key-down
                                                  (fn [e]
                                                    (when
                                                      (or (= (.-which e) 13)
                                                          (= (.-keyCode e) 13))
                                                      (send-message!
                                                        app-state (create-msg input-name input-text))
                                                      (om/update-state! comp assoc :input-text "")))
                             :value input-text}))))

(defn send-button [comp input-text input-name]
  (let [app-state (om/props comp)]
    (dom/div #js {:className "center-xs"}
             (ui/raised-button
               {:label "Send"
                :on-touch-tap
                       #(do
                          (send-message! app-state (create-msg input-name input-text))
                          (om/update-state! comp assoc :input-text ""))}))))

(defn message-item [{:keys [text name date]}]
  (ui/list-item {:primary-text
                                       (dom/div nil name
                                                (dom/small nil (str " wrote " (format-time date))))
                 :secondary-text text
                 :secondary-text-lines 2
                 :key (uuid (str date))}))

;; React App
(defui App
       Object
       (componentWillMount [this]
                           (om/set-state!
                             this
                             {:input-name ""
                              :input-text ""
                              :snackbar {:message "hello"
                                         :open false}}))
       (render [this]
               (let [app-state (om/props this)
                     {:keys [input-name input-text snackbar]} (om/get-state this)]
                 (ui/mui-theme-provider
                   {:mui-theme (ui/get-mui-theme)}
                   (html
                     [:div.col-xs-12.mar-top-10.row
                      (ui/snackbar {:open (:open snackbar) :message (:message snackbar)})
                      [:div.col-xs-3]
                      [:div.col-xs-6
                       (ui/paper {:className "mar-top-20"}
                                 (ui/list
                                   nil
                                   (name-field this input-name)
                                   (message-field this input-text input-name)
                                   (send-button this input-text input-name)
                                   (ui/subheader nil "Messages")
                                   (mapv message-item (sort-by :date > (vals app-state)))
                                   (ui/divider nil)))]])))))

(def reconciler
  (om/reconciler {:state mydb/val-atom}))

(defn main [& args]
  (go-try S
          (def client-state (<? S (mydb/setup-replikativ)))
          (.error js/console "INITED")))

;; for figwheel not in main
(om/add-root! reconciler App (.getElementById js/document "app"))