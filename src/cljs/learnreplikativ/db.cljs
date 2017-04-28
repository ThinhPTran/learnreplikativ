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

;; This part is for share state
(def user "trphthinh@gmail.com")
(def ormap-id #uuid "7d274663-9396-4247-910b-409ae35fe98d")
(def uri "ws://127.0.0.1:31744")

;; This part is for local state
(defn gentabledata [len]
  (reduce #(conj %1 [%2 %2 0])
          []
          (map #(* % 5) (range 100 (+ 100 len)))))

(def init-tableconfig {:colHeaders ["MD" "TVD" "Deviation"]
                       :data        (gentabledata 15)
                       :rowHeaders  false
                       :contextMenu true})

(def globalconfig
  (atom {:tableconfig init-tableconfig}))

(def global-users
  (atom {:user/names ["No users"]}))

(def local-login
  (atom {:user/name nil
         :input-text "sample input"}))

(def global-states
  (atom {:name "Global cummulative states"
         :totalactions 0
         :currentpick 0
         :tableconfig init-tableconfig
         :totallistactions ["No actions"]
         :listactions ["No actions"]}))

(def local-states
  (atom {:name "Local user states"
         :tableconfig init-tableconfig
         :listactions nil}))


;;; Setup on client to communicate.
(declare client-state)
;; this is the only state changing function
(defn send-message! [app-state msg]
  (s/assoc! (:stage client-state)
            [user ormap-id]
            (random-uuid)
            [['assoc msg]]))
;;; Don't touch to the part above.

;; Have a look at the replikativ "Get started" tutorial to understand how the
;; replikativ parts work: http://replikativ.io/tut/get-started.html

(def stream-eval-fns
  {'assoc (fn [a new]
            (swap! a assoc (uuid new) new)
            a)
   'dissoc (fn [a new]
             (swap! a dissoc (uuid new))
             a)})

(defonce app-state (atom {}))

;; Handle changes
(defn handle-user-change-MD [tableconfig action]
  (let [dataTable (:data tableconfig)
        rowIdx (:row action)
        colIdx (:col action)
        newMD (:val action)
        tmpDataTable1 (assoc-in dataTable [rowIdx colIdx] newMD)
        tmpDataTable1 (vec (sort #(compare (get %1 0) (get %2 0)) tmpDataTable1))
        tmpDataTable2 (assoc-in tmpDataTable1 [0 2] (* 180.0
                                                       (/
                                                         (js/Math.acos
                                                           (/ (double (get-in tmpDataTable1 [0 1])) (double (get-in tmpDataTable1 [0 0]))))
                                                         js/Math.PI)))
        newDataTable (reduce (fn [data rowIdx]
                               (let [md1 (get-in data [(- rowIdx 1) 0])
                                     md2 (get-in data [rowIdx 0])
                                     tvd1 (get-in data [(- rowIdx 1) 1])
                                     tvd2 (get-in data [rowIdx 1])
                                     dev3 (* 180.0
                                             (/
                                               (js/Math.acos (/ (double (- tvd1 tvd2)) (double (- md1 md2))))
                                               js/Math.PI))]
                                 (assoc-in data [rowIdx 2] dev3)))
                             tmpDataTable2
                             (range 1 (count tmpDataTable2)))
        newtableconfig (assoc tableconfig :data newDataTable)]
    newtableconfig))

(defn handle-user-change-TVD [tableconfig action]
  (let [dataTable (:data tableconfig)
        rowIdx (:row action)
        colIdx (:col action)
        newTVD (:val action)
        tmpDataTable1 (assoc-in dataTable [rowIdx colIdx] newTVD)
        tmpDataTable2 (assoc-in tmpDataTable1 [0 2] (* 180.0
                                                       (/
                                                         (js/Math.acos
                                                           (/ (double (get-in tmpDataTable1 [0 1])) (double (get-in tmpDataTable1 [0 0]))))
                                                         js/Math.PI)))
        newDataTable (reduce (fn [data rowIdx]
                               (let [md1 (get-in data [(- rowIdx 1) 0])
                                     md2 (get-in data [rowIdx 0])
                                     tvd1 (get-in data [(- rowIdx 1) 1])
                                     tvd2 (get-in data [rowIdx 1])
                                     dev3 (* 180.0
                                             (/
                                               (js/Math.acos (/ (double (- tvd1 tvd2)) (double (- md1 md2))))
                                               js/Math.PI))]
                                 (assoc-in data [rowIdx 2] dev3)))
                             tmpDataTable2
                             (range 1 (count tmpDataTable2)))
        newtableconfig (assoc tableconfig :data newDataTable)]
    newtableconfig))

(defn handle-user-change-Deviation [tableconfig action]
  (let [dataTable (:data tableconfig)
        rowIdx (:row action)
        colIdx (:col action)
        newDeviation (:val action)
        tmpDataTable1 (assoc-in dataTable [rowIdx colIdx] newDeviation)
        tmpDataTable2 (assoc-in tmpDataTable1 [0 1] (* (get-in tmpDataTable1 [0 0]) (Math/cos (* (/ (get-in tmpDataTable1 [0 2]) 180.0) Math/PI))))
        newDataTable (reduce (fn [data rowIdx]
                               (let [md1 (get-in data [(- rowIdx 1) 0])
                                     md2 (get-in data [rowIdx 0])
                                     tvd1 (get-in data [(- rowIdx 1) 1])
                                     dev2 (get-in data [rowIdx 2])
                                     tvd2 (+ tvd1 (* (- md2 md1) (js/Math.cos (* (/ dev2 180.0) js/Math.PI))))]
                                 (assoc-in data [rowIdx 1] tvd2)))
                             tmpDataTable2
                             (range 1 (count tmpDataTable2)))
        newtableconfig (assoc tableconfig :data newDataTable)]
    newtableconfig))

(defn handle-table-actions [tableconfig action]
  (let [colIdx (:col action)]
    (.log js/console "action: " action)
    (cond
      (= 0 colIdx) (handle-user-change-MD tableconfig action)
      (= 1 colIdx) (handle-user-change-TVD tableconfig action)
      (= 2 colIdx) (handle-user-change-Deviation tableconfig action))))

(defn handle-global-table [data]
  (let [myinitconfig (:tableconfig @globalconfig)
        newtableconfig (reduce (fn [indata action]
                                 (handle-table-actions indata action)) myinitconfig data)]
    (.log js/console "myinitconfig: " myinitconfig)
    (.log js/console "newtableconfig: " newtableconfig)
    (swap! global-states assoc :tableconfig newtableconfig)))

(defn handle-local-table [data]
  (let [myinitconfig (:tableconfig @globalconfig)
        newtableconfig (reduce (fn [indata action]
                                 (handle-table-actions indata action)) myinitconfig data)]
    (swap! local-states assoc :tableconfig newtableconfig)))

(defn handle-addUser [addUserActions]
  (let [usernames (mapv #(get-in % [:val :name]) addUserActions)]
    (.log js/console "usernames: " usernames)
    (swap! global-users assoc :user/names usernames)))

(defn handle-setTableVal [rawactions]
  (let [actions (mapv #(get % :val) rawactions)
        user (:user/name @local-login)
        localactions (filterv #(= user (:user %)) actions)]
    (.log js/console "setactions: " actions)
    (swap! local-states assoc :listactions localactions)
    (swap! global-states assoc :listactions actions)
    (handle-global-table actions)
    (handle-local-table localactions)))

(defn handle-changes []
  (.log js/console "app-state: " @app-state)
  (let [allactions (vals @app-state)
        user (:user/name @local-login)
        addUseractions (->> allactions
                         (filterv #(= :addUser (:act %)))
                         (sort #(compare (:inst %1) (:inst %2))))
        setTableValacts (->> allactions
                         (filterv #(= :setTableVal (:act %)))
                         (sort #(compare (:inst %1) (:inst %2))))]
    (.log js/console "all actions: " allactions)
    (.log js/console "addUser actions: " addUseractions)
    (.log js/console "setTableVal actions: " setTableValacts)
    (handle-addUser addUseractions)
    (if (some? user)
       (handle-setTableVal setTableValacts))))

(add-watch app-state :key #(handle-changes))


;; Setup sync state
(defn setup-replikativ []
  (go-try S
          (let [local-store (<? S (new-mem-store))
                local-peer (<? S (client-peer S local-store))
                stage (<? S (create-stage! user local-peer))
                stream (stream-into-identity! stage
                                              [user ormap-id]
                                              stream-eval-fns
                                              app-state)]
            (<? S (s/create-ormap! stage
                                   :description "messages"
                                   :id ormap-id))
            (connect! stage uri)
            {:store local-store
             :stage stage
             :stream stream
             :peer local-peer})))

(defn setupclientdata []
  (go-try S
          (def client-state (<? S (setup-replikativ)))))






