+active
   :  my_condition(Pathology, _) & at(pos(X, Y)) & not reported & .my_name(Me)
   <- +reported;
      .print("[CALL] ", Me, ": ", Pathology, " at (", X, ",", Y, ")");
      .send(control_center, achieve, handle_emergency(Me, pos(X, Y), Pathology)).

+my_condition(Pathology, _)
   :  active & at(pos(X, Y)) & not reported & .my_name(Me)
   <- +reported;
      .print("[CALL] ", Me, ": ", Pathology, " at (", X, ",", Y, ")");
      .send(control_center, achieve, handle_emergency(Me, pos(X, Y), Pathology)).

+at(pos(X, Y))
   :  active & my_condition(Pathology, _) & not reported & .my_name(Me)
   <- +reported;
      .print("[CALL] ", Me, ": ", Pathology, " at (", X, ",", Y, ")");
      .send(control_center, achieve, handle_emergency(Me, pos(X, Y), Pathology)).
