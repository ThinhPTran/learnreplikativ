(ns learnreplikativ.db
  (:require [konserve.memory :refer [new-mem-store]]
            [replikativ.peer :refer [client-peer]]
            [replikativ.stage :refer [create-stage! connect!
                                      subscribe-crdts!]]
            [cljs.core.async :refer [>! chan timeout]]
            [superv.async :refer [S] :as sasync]
            [replikativ.crdt.ormap.realize :refer [stream-into-identity!]]
            [replikativ.crdt.ormap.stage :as s])
  (:require-macros [superv.async :refer [go-try <? go-loop-try]]
                   [cljs.core.async.macros :refer [go-loop]]))


(enable-console-print!)

;; 1. app constants
(def user "trphthinh@gmail.com")
(def ormap-id #uuid "7d274663-9396-4247-910b-409ae35fe98d")
(def uri "ws://127.0.0.1:31744")

;; Have a look at the replikativ "Get started" tutorial to understand how the
;; replikativ parts work: http://replikativ.io/tut/get-started.html

(def stream-eval-fns
  {'assoc (fn [a new]
            (swap! a assoc (uuid new) new)
            a)
   'dissoc (fn [a new]
             (swap! a dissoc (uuid new))
             a)})


(defonce val-atom (atom {}))
(defonce my-atom (atom {}))

(add-watch val-atom :key #(.log js/console "thinh print val-atom: " @val-atom))
;(add-watch my-atom :key #(.log js/console "thinh print my-atom: " @my-atom))

(defn setup-replikativ []
  (go-try S
          (let [local-store (<? S (new-mem-store))
                local-peer (<? S (client-peer S local-store))
                stage (<? S (create-stage! user local-peer))
                stream (stream-into-identity! stage
                                              [user ormap-id]
                                              stream-eval-fns
                                              val-atom)]
            (<? S (s/create-ormap! stage
                                   :description "messages"
                                   :id ormap-id))
            (connect! stage uri)
            {:store local-store
             :stage stage
             :stream stream
             :peer local-peer})))




