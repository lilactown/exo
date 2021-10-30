(ns exo.example.data)


(defn load-people
  []
  (js/Promise.
   (fn [res _]
     (js/setTimeout
      #(res [{:person/id 0 :person/name "Rachel"}
             {:person/id 1 :person/name "Marco"}
             {:person/id 2 :person/name "Cassie"}
             {:person/id 3 :person/name "Jake"}
             {:person/id 4 :person/name "Tobias"}
             {:person/id 5 :person/name "Ax"}])
      200))))


(defn load-person
  [id]
  (js/Promise.
   (fn [res _]
     (js/setTimeout
      #(res (case id
              0 {:person/id 0
                 :person/name "Rachel"
                 :person/species :human}
              1 {:person/id 1
                 :person/name "Marco"
                 :person/species :human}
              2 {:person/id 2
                 :person/name "Cassie"
                 :person/species :human}
              3 {:person/id 3
                 :person/name "Jake"
                 :person/species :human}
              4 {:person/id 4
                 :person/name "Tobias"
                 :person/species :human}
              5 {:person/id 5
                 :person/name "Ax"
                 :person/species :andalite}))
      200))))


(defn load-best-friend
  [id]
  (js/Promise.
   (fn [res rej]
     (js/setTimeout
      #(case id
         1 (res {:person/id 3})
         3 (res {:person/id 1})
         (rej {:status 404
               :message "No best friend data available."}))
      500))))
