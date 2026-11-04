(require '[clj-kondo.core :as kondo]
         '[cljfmt.core :as fmt])
(let [[mode & paths] *command-line-args*]
  (doseq [path paths]
    (let [source (slurp path)
          formatted (fmt/reformat-string source)]
      (if (= mode "fix")
        (when-not (= source formatted) (spit path formatted))
        (assert (= source formatted) (str "Formatting differs: " path)))))
  (let [result (kondo/run! {:lint (vec paths) :cache false})]
    (doseq [finding (:findings result)] (prn finding))
    (prn (:summary result))
    (System/exit (if (zero? (get-in result [:summary :error])) 0 1))))
