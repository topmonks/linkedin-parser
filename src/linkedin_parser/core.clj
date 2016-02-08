(ns linkedin-parser.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure.string :as string])
  (:import [org.apache.pdfbox.pdmodel PDDocument]
           [linkedin.parser PDFText2HTML]
           (java.io ByteArrayInputStream)))

(def present ["Present", "Sou#asnost"])

(def known-sections
  {"Summary" :summary
   "Honors and Awards" :honors-and-awards
   "Skills & Expertise" :skills-and-expertise
   "Languages" :languages
   "Experience" :experience
   "Volunteer Experience" :volunteer-experience
   "Projects" :projects
   "Education" :education

   "Souhrn" :summary
   "Ceny a ocenění" :honors-and-awards
   "Dovednosti a odbornosti" :skills-and-expertise
   "Jazyky" :languages
   "Pracovní zkušenosti" :experience
   "Zkušenosti z dobro#inné práce" :volunteer-experience
   "Projekty" :projects
   "Vzd#lání" :education})

(def language-proficiency
  {"Native or bilingual proficiency" :native
   "Full professional proficiency" :full-professional
   "Professional working proficiency" :professional-working
   "Limited working proficiency" :limited-working
   "Elementary proficiency" :elementary

   "Znalost na úrovni rodilého nebo dvojjazy#ného mluv#ího" :native
   "Pln# profesionální znalost" :full-professional
   "Znalost umož#ující profesionální práci" :professional-working
   "Znalost umož#ující omezenou práci" :limited-working
   "Elementární znalost" :elementary})

(def months
  {"January" "01"
   "February" "02"
   "March" "03"
   "April" "04"
   "May" "05"
   "June" "06"
   "July" "07"
   "August" "08"
   "September" "09"
   "October" "10"
   "November" "11"
   "December" "12"

   "leden" "01"
   "únor" "02"
   "b#ezen" "03"
   "duben" "04"
   "kv#ten" "05"
   "#erven" "06"
   "#ervenec" "07"
   "srpen" "08"
   "zá#í" "09"
   "#íjen" "10"
   "listopad" "11"
   "prosinec" "12"})

(defn iso-date [date]
  (when-not (some #{date} present)
    (let [[month year] (string/split date #"\s")]
      (if-not year
        (some-> month (str "-01-01"))
        (str year "-" (get months month) "-01")))))

(defn read-pdf [pdf]
  (with-open [pd (PDDocument/load pdf)]
    (let [stripper (PDFText2HTML. "UTF-16")]
      (.getText stripper pd))))

(defn input-stream [^String string]
  (ByteArrayInputStream. (.getBytes (.trim string))))

(defn preprocess [^String str]
  (some-> str
    (string/replace #"((Page)|(Stránka))\d+" "")
    (string/replace #"\n+" "\n\n")
    (string/replace #"<h2>" "</section><section><h2>")
    (string/replace #"<h1>" "</section><h1>")
    (string/replace #"</b>\n\n<b>" "\n\n")))

(defn markup [input]
  (some-> input read-pdf preprocess input-stream html/html-resource))

(defn sections [markup]
  (map
    (fn [s]
      {:name (string/trim (html/text (first (html/select s [:section :h2]))))
       :content (remove #(= :h2 (:tag %)) (s :content))})
    (html/select markup [:section])))

(defn experience [[key val]]
  (let [[title company] (some-> key html/text string/trim (string/split #"((at)|(ve\:))"))
        [duration & rest] (some-> val string/trim string/split-lines)
        [from to] (some-> duration (string/split #"-"))
        [to _] (some-> to (string/split #"\("))]
    {:title (some-> title string/trim)
     :company (some-> company string/trim)
     :from (some-> from string/trim iso-date)
     :to (some-> to string/trim iso-date)
     :current (or (some #(string/includes? duration %) present) false)
     :description (some->> rest (remove #{""}) (string/join " "))}))

(defn volunteer-experience [[key val]]
  (let [[title company] (some-> key html/text string/trim (string/split #"((at)|(ve\:))"))
        [duration] (some-> val string/trim string/split-lines)
        [from to] (some-> duration (string/split #"-"))
        [to] (some-> to (string/split #"\("))]
    {:role (some-> title string/trim)
     :organization (some-> company string/trim)
     :from (some-> from string/trim iso-date)
     :to (some-> to string/trim iso-date)}))

(defn project [[key val]]
  (let [name (some-> key html/text string/trim)
        [duration & rest] (some-> val string/trim string/split-lines)
        [from to] (some-> duration (string/split #"((to)|(-))"))]
    {:name name
     :from (some-> from string/trim iso-date)
     :to (some-> to string/trim iso-date)
     :current (or (some #(string/includes? duration %) present) false)
     :description (some->> rest (remove #{""}) (string/join " "))}))

(defn education [[key val]]
  (let [name (some-> key html/text string/trim)
        [tmp description] (some->> val string/trim string/split-lines (remove #{""}))
        [duration field grade] (some-> tmp string/trim (string/split #", ") reverse)
        [from to] (some-> duration (string/split #"-"))]
    {:name name
     :from (some-> from string/trim iso-date)
     :to (some-> to string/trim iso-date)
     :current (or (some #(string/includes? duration %) present) false)
     :grade grade
     :field-of-study field
     :description description}))

(defn languages [[key val]]
  (let [name (some-> key html/text string/trim)
        proficiency (some-> val string/trim (string/replace #"[\(|\)]" ""))]
    {:language name
     :proficiency (language-proficiency proficiency)}))

(defn items-of [section]
  (->>
    (:content section)
    (remove #{"\n\n"})
    (partition 2 2 [nil])
    (mapv vec)
    (into {})))

(defmulti section (fn [s] (some-> s :name known-sections)))
(defmethod section :default [s] s)

(defmethod section :summary [section]
  (->>
    (:content section)
    first
    string/split-lines
    (remove #{""})
    (string/join " ")
    (assoc section :content)))

(defmethod section :honors-and-awards [section]
  (->>
    (:content section)
    first
    string/split-lines
    (remove #{""})
    (string/join " ")
    (assoc section :content)))

(defmethod section :skills-and-expertise [section]
  (->>
    (html/select (:content section) [:b])
    first
    html/text
    string/split-lines
    (remove #{""})
    (mapv string/trim)
    (assoc section :content)))

(defmethod section :experience [section]
  (->>
    (items-of section)
    (mapv experience)
    (assoc section :content)))

(defmethod section :volunteer-experience [section]
  (->>
    (items-of section)
    (mapv volunteer-experience)
    (assoc section :content)))

(defmethod section :projects [section]
  (->>
    (items-of section)
    (mapv project)
    (assoc section :content)))

(defmethod section :education [section]
  (->>
    (items-of section)
    (mapv education)
    (assoc section :content)))

(defmethod section :languages [section]
  (->>
    (items-of section)
    (mapv languages)
    (assoc section :content)))

(defn parse-sections [html]
  (->>
    html
    sections
    (filter #(contains? known-sections (:name %)))
    (mapv section)
    (mapv (fn [{:keys [name content]}] [(known-sections name) content]))
    (into {})))

(defn parse-name [html]
  (->
    html
    (html/select [:title])
    first
    html/text
    string/trim))

(defn parse-headline-and-mail [html]
  (->>
    (html/select html [:body])
    first
    :content
    next next first
    string/split-lines
    (remove #{""})))

(defn parse-pdf
  "Parses given LinkedIn profile PDF to data structures.

  input - can be anything `org.apache.pdfbox.pdmodelPDDocument/load` takes (File, URL, InputStream or fileName)

  Return map with parsed sections."
  [input]
  (let [html (markup input)
        sections-data (parse-sections html)
        name (parse-name html)
        [headline email] (parse-headline-and-mail html)]
    (assoc sections-data
      :full-name name
      :headline headline
      :email email)))