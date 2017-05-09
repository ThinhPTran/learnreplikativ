(ns learnreplikativ.events
  (:require [clojure.string :as str]
            [learnreplikativ.db :as mydb :refer [send-message! app-state]]
            [learnreplikativ.utils :as utils :refer [create-msg]]))

(defn in?
  "true if coll contains elm"
  [coll elm]
  (some #(= elm %) coll))

(defn user-login [name]
  (if (str/blank? name)
    (js/alert "Please enter a user name")
    (do
      (.log js/console (str "Logging in with user: " name))
      (swap! mydb/local-login assoc :user/name name)
      (let [usernames (:user/names @mydb/global-users)]
        (.log js/console (str "Usernames: " usernames))
        (if (in? usernames name)
          (.log js/console (str "User existed!!!"))
          (send-message! app-state (create-msg :addUser {:name name :pass "mypass"})))))))

(defn get-usernames []
  (do
    (.log js/console (str "Getting usernames!!!"))))

(defn get-cum-actions []
  (do
    (.log js/console (str "Getting cummulative actions!!!"))))

(defn set-action [rawchangeDatas]
  (when (and (some? rawchangeDatas) (some? (:user/name @mydb/local-login)))
    (let [changeDatas (mapv #(assoc-in % [3] (js/parseFloat (get-in % [3]))) rawchangeDatas)]
      (.log js/console "set-action: " changeDatas)
      (doseq [changeData changeDatas]
        (.log js/console "changeData: " changeData)
        (send-message! app-state (create-msg :setTableVal {:user (:user/name @mydb/local-login)
                                                           :row (get-in changeData [0])
                                                           :col (get-in changeData [1])
                                                           :val (get-in changeData [3])
                                                           :inst (.getTime (js/Date.))}))))))

(defn set-history-point [idx]
  (do
    (.log js/console "set-history-point!!!")))