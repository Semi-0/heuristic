(ns lain-harness.pi-session-test
  (:require [clojure.java.io :as io]
            [clojure.test :refer [deftest is]]
            [lain-harness.pi-session :as pi]))

(defn fixture [source]
  (let [directory (.toFile (java.nio.file.Files/createTempDirectory "lain-pi-test" (make-array java.nio.file.attribute.FileAttribute 0)))
        bridge (io/file directory "bridge.mjs")]
    (spit bridge source)
    {:bridge (.getPath bridge) :evidence (.getPath directory) :timeout-ms 1000}))

(deftest rejects-incomplete-reply
  (let [options (fixture "process.stdin.once('data',()=>console.log(JSON.stringify({type:'reply',kind:'reply',text:'bad', 'finish-reason':'length','truncated?':true}))); ")]
    (is (thrown? Exception ((pi/transport options) {:id "invalid" :text "x" :cancelled? (constantly false)})))))

(deftest cancellation-stops-owned-process
  (let [options (fixture "setInterval(()=>{},1000); ") start (System/nanoTime)]
    (is (thrown? InterruptedException ((pi/transport options) {:id "cancel" :text "x" :cancelled? (constantly true)})))
    (is (< (/ (- (System/nanoTime) start) 1e6) 2500))))

(deftest inspection-is-observation-only
  (let [seen (atom []) options (fixture "let n=0;process.stdin.on('data',()=>{if(n++===0)console.log(JSON.stringify({type:'tool',id:'t',name:'inspect_lain',arguments:{name:'f'}}));else console.log(JSON.stringify({type:'reply',kind:'reply',text:'source','finish-reason':'stop','truncated?':false}));});")
        reply ((pi/transport (assoc options :inspect #(do (swap! seen conj %) "observed")))
               {:id "tool" :text "x" :cancelled? (constantly false)})]
    (is (= [{:name "f"}] @seen))
    (is (= :reply (:kind reply)))
    (is (= "source" (:text reply)))))
