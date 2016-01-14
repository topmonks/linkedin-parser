(ns linkedin-parser.core
  (:require [net.cgrand.enlive-html :as html]
            [clojure.string :as string])
  (:import [org.apache.pdfbox.pdmodel PDDocument]
           [linkedin.parser PDFText2HTML]
           (java.net URL)
           (java.io ByteArrayInputStream)))


(defn text-of-pdf
  [stream]
  (with-open [pd (PDDocument/load stream)]
    (let [stripper (PDFText2HTML. "UTF-16")]
      (.getText stripper pd))))

(defn string-to-stream [string]
  (ByteArrayInputStream.
    (.getBytes (.trim string))))

(defn preprocess [str]
  (-> str
    (string/replace #"Page\d+" "")
    (string/replace #"\n+" "\n\n")
    (string/replace #"<h2>" "</section><section><h2>")
    (string/replace #"</b>\n\n<b>" "")))

(defn markup [url] (-> url text-of-pdf preprocess string-to-stream html/html-resource))

(defn sections [markup]
  (map
    (fn [s]
      {:name (string/trim (html/text (first (html/select s [:section :h2]))))
       :content (remove #(= :h2 (:tag %)) (s :content))})
    (html/select markup [:section])))

(defn experience [[key val]]
  (let [[title company] (some-> key html/text string/trim (string/split #"\sat\s"))
        [duration & rest] (some-> val string/trim string/split-lines)
        [from to] (some-> duration (string/split #"-"))
        [to _] (some-> to (string/split #"\("))]
    {:title (some-> title string/trim)
     :company (some-> company string/trim)
     :from (some-> from string/trim)
     :to (some-> to string/trim)
     :current (string/includes? duration "Present")
     :description (some->> rest (remove #{""}) (string/join " "))}))

(defn volunteer-experience [[key val]]
  (let [[title company] (some-> key html/text string/trim (string/split #"at"))
        [duration] (some-> val string/trim string/split-lines)
        [from to] (some-> duration (string/split #"-"))
        [to] (some-> to (string/split #"\("))]
    {:role (some-> title string/trim)
     :organization (some-> company string/trim)
     :from (some-> from string/trim)
     :to (some-> to string/trim)}))

(defn project [[key val]]
  (let [name (some-> key html/text string/trim)
        [duration & rest] (some-> val string/trim string/split-lines)
        [from to] (some-> duration (string/split #"\sto\s"))]
    {:name name
     :from (some-> from string/trim)
     :to (some-> to string/trim)
     :current (string/includes? duration "Present")
     :description (some->> rest (remove #{""}) (string/join " "))}))

(defn education [[key val]]
  (let [name (some-> key html/text string/trim)
        [duration field grade] (some-> val string/trim (string/split #", ") reverse)
        [from to] (some-> duration (string/split #"\s-\s"))]
    {:name name
     :from (some-> from string/trim)
     :to (some-> to string/trim)
     :current (string/includes? duration "Present")
     :grade grade
     :field-of-study field}))

(defn languages [[key val]]
  (let [name (some-> key html/text string/trim)
        proficiency (some-> val string/trim)]
    {:language name
     :proficiency proficiency}))

(defn items-of [section]
  (->>
    (:content section)
    (remove #{"\n\n"})
    (apply hash-map)))

(defmulti parse-section (fn [s] (:name s)))
(defmethod parse-section :default [s] s)

(defmethod parse-section "Summary" [section]
  (->>
    (:content section)
    first
    string/split-lines
    (remove #{""})
    (string/join " ")
    (assoc section :content)))

(defmethod parse-section "Honors and Awards" [section]
  (->>
    (:content section)
    first
    string/split-lines
    (remove #{""})
    (string/join " ")
    (assoc section :content)))

(defmethod parse-section "Skills & Expertise" [section]
  (->>
    (html/select (:content section) [:b])
    first
    html/text
    string/split-lines
    (remove #{""})
    (mapv string/trim)
    (assoc section :content)))

(defmethod parse-section "Experience" [section]
  (->>
    (items-of section)
    (mapv experience)
    (assoc section :content)))

(defmethod parse-section "Volunteer Experience" [section]
  (->>
    (items-of section)
    (mapv volunteer-experience)
    (assoc section :content)))

(defmethod parse-section "Projects" [section]
  (->>
    (items-of section)
    (mapv project)
    (assoc section :content)))

(defmethod parse-section "Education" [section]
  (->>
    (items-of section)
    (mapv education)
    (assoc section :content)))

(defmethod parse-section "Languages" [section]
  (->>
    (items-of section)
    (mapv languages)
    (assoc section :content)))

(def known-sections
  {"Summary" :summary
   "Honors and Awards" :honors-and-awards
   "Skills & Expertise" :skills-and-expertise
   "Languages" :languages
   "Experience" :experience
   "Projects" :projects
   "Education" :education})

(defn parse-sections [html]
  (->>
    html
    sections
    (filter #(contains? known-sections (:name %)))
    (mapv parse-section)
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

(defn parse-pdf [stream]
  (let [html (markup stream)
        sections-data (parse-sections html)
        name (parse-name html)
        [headline email] (parse-headline-and-mail html)]
    (assoc sections-data
      :full-name name
      :headline headline
      :email email)))