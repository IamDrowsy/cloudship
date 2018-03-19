(ns cloudship.util.user-interact
  (:import [javax.swing JOptionPane JLabel JPanel JPasswordField
                        JFrame]))

(defn show-and-resolve-password-option-pane [title panel]
  (let [f (JFrame. ^String title)]
    (doto f
      (.setUndecorated true)
      (.setVisible true)
      (.setLocationRelativeTo nil))
    (let [result (JOptionPane/showOptionDialog nil panel title
                                               JOptionPane/OK_CANCEL_OPTION JOptionPane/PLAIN_MESSAGE
                                               nil
                                               (into-array String ["Ok" "Cancel"]) "Ok")]
      (.dispose f)
      result)))

(defn ^String prompt-password [target]
  (let [p (JPanel.)
        l (JLabel. (str "Password for " target))
        pw (JPasswordField. 30)]
    (doto p
      (.add l)
      (.add pw))
    (if (= (show-and-resolve-password-option-pane "Input Password" p)
           JOptionPane/OK_OPTION)
      (apply str (.getPassword pw)))))

(defn ask-to-continue! [infotext]
  (if (zero? (JOptionPane/showConfirmDialog nil infotext "Continue?" JOptionPane/OK_CANCEL_OPTION))
    true
    false))