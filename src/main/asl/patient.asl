+active <- !report_emergency.
+my_condition(_, _) <- !report_emergency.
+at(pos(_, _)) <- !report_emergency.

@patient_report[atomic]
+!report_emergency
   :  active & my_condition(Pathology, _) & at(pos(X, Y)) & not reported & .my_name(Me)
   <- +reported;
      .print("[CALL] ", Me, ": ", Pathology, " at (", X, ",", Y, ")");
      .send(control_center, achieve, handle_emergency(Me, pos(X, Y), Pathology)).

+!report_emergency.
