(ns neo4clj.internal.sanitize-test
  (:require [clojure.test :as t]
            [neo4clj.internal.sanitize :as sut]))

(t/deftest cypher-label
  (t/testing "Sanitation of a Clojure label to match the CYPHER style guide"
    (t/are [cypher label]
        (= cypher (sut/cypher-label label))
      "Car" :car
      "Car" "car"
      "Car" "Car"
      "BestFriend" :best-friend
      "BestFriend" "Best_friend"
      "NextBestFriend" :next-best_friend)))

(t/deftest cypher-labels
  (t/testing "Sanitation of a collection of Clojure labels to match the CYPHER style guide"
    (t/are [cyphers labels]
        (= cyphers (sut/cypher-labels labels))
      ["Car"] [:car]
      '("Car" "Friend" "BestFriend") ["car" :Friend :best_friend])))

(t/deftest cypher-property-key
  (t/testing "Sanitation of a Clojure property key to match the CYPHER style guide"
    (t/are [cypher key]
        (= cypher (sut/cypher-property-key key))
      "name" :name
      "name" "Name"
      "name" "name"
      "firstName" :first-name
      "firstName" :first_name
      "lastAndMiddleName" :last-and-middle_name
      "lastAndMiddleName" "last_And-middle_Name")))

(t/deftest cypher-relation-type
  (t/testing "Sanitation of a Clojure relation type to match the CYPHER style guide"
    (t/are [cypher type]
        (= cypher (sut/cypher-relation-type type))
      "FRIENDS" :friends
      "FRIENDS" "friends"
      "FRIENDS" "Friends"
      "FRIENDS" "FRIENDS"
      "BEST_FRIENDS" :best-friends
      "BEST_FRIENDS" :best_friends
      "BEST_FRIENDS" "best_Friends"
      "NEXT_BEST_FRIENDS" :next-best_friends
      "NEXT_BEST_FRIENDS" "next-Best_Friends")))

(t/deftest clj-label
  (t/testing "Sanitation of a CYPHER label to match the Clojure style guide"
    (t/are [label cypher]
        (= label (sut/clj-label cypher))
      :car "Car"
      :best-friend "BestFriend"
      :next-best-friend "NextBestFriend")))

(t/deftest clj-labels
  (t/testing "Sanitation of a collection of CYPHER labels to match the Clojure style guide"
    (t/are [labels cyphers]
        (= labels (sut/clj-labels cyphers))
      [:car] ["Car"]
      [:car :best-friend :next-best-friend] ["Car" "BestFriend" "NextBestFriend"])))

(t/deftest clj-lproperties
  (t/testing "Sanitation of a map of CYPHER properties to match the Clojure style guide"
    (t/are [properties cypher]
        (= properties (sut/clj-properties cypher))
      {:name "Joe"} {"name" "Joe"}
      {:first-name "Sue"} {"firstName" "Sue"}
      {:middle-and-last-name "Joel Anderson"} {"middleAndLastName" "Joel Anderson"}
      {:first-name "Jane" :last-name "Doe" :age 32} {"firstName" "Jane" "lastName" "Doe" "age" 32})))
